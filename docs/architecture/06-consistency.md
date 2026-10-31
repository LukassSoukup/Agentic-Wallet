# 06 Consistency, concurrency and replay

The mechanics that keep money right when things run at the same time, fail half-way or arrive twice. Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md). Every mechanism here has a required test (see the end).

## 1. Budget reservations under concurrency (invariant 5)
Two proposals, or a proposal and a step-up approval, can arrive at the same moment against the same budget. Both would read "enough left" and both would reserve.

- The ledger keeps one **budget-period row** per (mandate budget, period) and one per (global budget, period). It holds the reserved and counted totals in the base currency.
- Locking is done by `ledger`'s published API, which `decisioning` calls at the start of the decision's transaction; `decisioning` never touches ledger tables. **Every first decision and every step-up approval** locks every budget-period row it touches (a first decision does not yet know whether it will reserve) with `SELECT … FOR UPDATE` **at the start of its transaction**, in a fixed order (global first, then by mandate id) so two transactions can't deadlock. Then it reads the remaining amount, decides and writes, all under that lock.
- Isolation level `READ COMMITTED` plus these explicit row locks. No `SERIALIZABLE` retries.
- Rows for a period that doesn't exist yet are created with `INSERT … ON CONFLICT DO NOTHING` before they are locked.
- The duplicate, split-order and velocity checks read the ledger under the same locks, so they see every committed reservation.
- **What counts:** remaining = budget − reserved − counted. A reservation is made at APPROVE for the approved amount in the base currency at the decision's FX rate (for a connector quote with a variable final amount, the **ceiling**; the difference to the billed amount returning budget is an open item); it turns into *counted* when the checkout attempt reaches `COMPLETED`, at the same amount (the authorised and cleared amounts are reconciliation's business, not the ledger's); it is released on `FAILED` or `CANCELLED`. The exact counting rule with its edges is written in T-003 from this.
- **The purchase count** of a mandate (one for a one-shot watch) is kept the same way, in `ledger` only: one **purchase-count row** per mandate with reserved and counted purchases, locked with `SELECT … FOR UPDATE` after the budget-period rows, by mandate id, so two runs racing for the last purchase get exactly one APPROVE. It is reserved at APPROVE, counted at `COMPLETED`, released on `FAILED` or `CANCELLED`, held while `UNKNOWN`. Each counted purchase records the mandate version it was approved under, so the first-run rule for a merchant-derived list can ask "any completed purchase under this version?". A per-purchase ceiling alone would let two offers at two merchants both pass; the count is what makes "buy one ticket" mean one. `mandates` reads it through `ledger`'s API to raise `MandateFulfilled`.

## 2. Every step-up outcome is a second decision
A STEP_UP ends in exactly one of: the user approves, the user declines, the question expires (the scheduled job in `decisioning`; the expiry is set per decision as `min(the ceiling in settings, the stored session expiry − a margin)` and stored in the `DecisionInput`), the mandate is revoked or fulfilled, the agent is stopped. **Each of these is a new decision record** in `decisioning`, linked to the first, with its own `DecisionInput`, its reason code, its audit row and its `DecisionMade` outbox row, committed in one transaction. A user's decline, an expiry, a revocation, a fulfilment or a stop produce DECLINE with the corresponding reason and lock nothing. An approval runs the re-check:
1. lock the budget-period rows (section 1);
2. re-read the mandate version, the ledger and the checkout session's **stored** expiry (no merchant call inside the transaction);
3. decide APPROVE or DECLINE with reasons;
4. on APPROVE, write the reservation, the pre-approval, the audit record and the outbox row.

If the stored session has expired, the answer is DECLINE with the reason "the merchant's cart expired; propose again". The agent's next proposal opens a new session, outside any transaction.

## 3. Checkout attempts
After an APPROVE, `payments` runs the checkout as a persisted state machine, one **checkout attempt** per approved decision (unique on the decision id).

```
CREATED ──▶ CREDENTIAL_REQUESTED ──▶ COMPLETING ──▶ COMPLETED
   │           │          │             │
   │           │          └──▶ PENDING_USER ──(user finishes the network's own step)──▶ COMPLETING
   │           ├──▶ FAILED              ├──▶ FAILED
   │           └──▶ UNKNOWN             └──▶ UNKNOWN ──(resolved)──▶ COMPLETED | FAILED
   └──▶ CANCELLED  (mandate revoked or agent stopped before any credential was requested)
```

- Each transition is committed **before** the remote call it announces. The credential exists only in memory between `CREDENTIAL_REQUESTED` and the completion call.
- **At most one credential request per decision.** If a listener finds an attempt already in `CREDENTIAL_REQUESTED` or `COMPLETING` (a crash, or a redelivered event), it never asks for a second credential. It resolves the attempt by reading the merchant's checkout session status (and the network confirmation where one exists). Completed means `COMPLETED`. Not completed means `FAILED`, and the credential expires unused.
- **A timeout is `UNKNOWN`, never `FAILED`.** While an attempt is `UNKNOWN`, the reservation and the pre-approval stay in force. Entering `UNKNOWN` publishes `CheckoutOutcomeUnknown`, so `activity` shows the honest state and `notifications` can tell the user. A resolver job queries the session status. If the hook or reconciliation sees an authorisation that matches, the attempt is `COMPLETED`. If the pre-approval window ends without one, it is `FAILED`.
- **`PENDING_USER`:** the network answered that the cardholder must authenticate (its own passkey or app step). The reservation and the pre-approval stay; the `CheckoutPendingUser` event lets `notifications` tell the user; the attempt continues when the network reports success, and goes to `FAILED` when the network's pending state ends without it, the **stored checkout session expiry** passes, or the pre-approval window closes, whichever is first.
- **`COMPLETED`:** `payments` calls `ledger.count(decisionId)` in the same transaction as the state change and the `CheckoutCompleted` outbox row.
- **`CANCELLED`:** a mandate revocation or an agent stop reaches an attempt that has not yet requested a credential. The reservation and the pre-approval are released; the event is `CheckoutCancelled`. An attempt that has already requested its credential runs to its end: the user approved that purchase under a mandate that was valid at the time, and the money may already be committed at the merchant.
- **On `FAILED` or `CANCELLED`:** `payments`' application service calls the published APIs `ledger.release(decisionId)` and `authorisation.cancel(decisionId)` **in its own transaction**, together with the attempt's state change and the `CheckoutFailed` (with its reason) or `CheckoutCancelled` outbox row, so the three facts change atomically. Events are for read models and reconciliation, never for keeping these three consistent. Afterwards `payments` cancels the network instruction where the network allows it (only before its credentials are used).
- Idempotency keys toward the merchant and the network come from the `purchaseKey` and the attempt id, so a retried call is the same call.
- **Connector orders** ([ADR-0008](../adr/ADR-0008-merchant-connectors-and-the-connector-catalogue.md)) have no credential: the attempt goes `CREATED → COMPLETING → COMPLETED | FAILED | UNKNOWN | PENDING_USER`. `COMPLETING` is committed before the order call, which carries the quote id and our idempotency key as the order reference. `UNKNOWN` and `PENDING_USER` are resolved only by looking that reference up at the merchant through the `MerchantAccount` port (the resolver job, with the link's token); the order call is never repeated blindly. `PENDING_USER` here means the merchant requires the cardholder's authentication on a card-on-file order, bounded by the stored quote or slot-hold expiry. The resolution window runs to the slot's end plus the manifest's authorisation lag. An ended link keeps a resolution-only use of its token until every attempt and reconciliation read for it is final, at the latest until the last order's slot end plus the reconciliation window from `settings`; a connector order re-reads the card on file when it enters `COMPLETING` and goes `FAILED` with "card on file changed" if it no longer matches the decision's route; when a lookup is nevertheless impossible, the attempt stays `UNKNOWN` and a mismatch case is opened, never a silent `FAILED` that would release approved money while the merchant delivers and charges. The pre-approval written at the ceiling matches the merchant's authorisation only when the card on file is the enrolled agent card; in the one-line mode it expires unused and reconciliation works from the merchant's order record. A connector turned off for an instance or an agent stopped cancels that instance's attempts still in `CREATED` at that merchant like a revocation does; a link ended or a connector disabled or suspended cancels every attempt in `CREATED` there.

## 4. Pre-approval consumption (the bank's hook)
A pre-approval matches **one** authorisation. The hook responder owns the fact that a pre-approval was used:
- table `hook.pre_approval_consumption` with a **unique constraint on the pre-approval id**;
- answering a match inserts that row and the call-log row in one transaction, **in shadow mode as well as in enforcing mode**, so that a second authorisation is recorded as "would decline" in shadow mode too. If the insert hits the unique constraint, the pre-approval was already used, and the answer is "no match";
- a repeated delivery of the same processor authorisation id returns the stored answer (deduplication comes before matching);
- **incremental authorisations** carry the processor's original authorisation id; they are matched against the pre-approval already consumed by that id, and accepted while the cumulative amount stays at or below the approved amount. Consumption is per pre-approval; it does not block increments of the authorisation that consumed it;
- a **second authorisation with a different id** (a merchant that authorises per shipment, or a replay) does not match. In shadow mode this is recorded; in enforcing mode it is declined and the case goes to reconciliation. Merchants that authorise in several parts are a known limitation ([05-open-items.md](05-open-items.md));
- a **reversal** of a matched authorisation is recorded, and reconciliation decides whether the purchase failed. It never re-opens the pre-approval for a different authorisation.
- `authorisation` in the backend reads the consumption and call-log rows by polling with a cursor (interval configurable, 1 s in the PoC) and turns them into `PreApprovalMatched` and `UnapprovedAuthorisationSeen` events.

## 5. Idempotency of proposals (invariant 7)
- The key is (**agent instance**, `purchaseKey`), so two agents can never collide. It is a **unique constraint** on the proposal table.
- **Concurrent delivery:** two deliveries of the same key may both pass the "seen?" check, both open a merchant session and both decide; the second commit hits the constraint. The handler then re-reads the stored proposal: equal binding fields return the stored decision (the duplicate session is left to expire), different binding fields are the conflict below. There is never a second decision for one key.
- "Same request" means the **binding fields** are equal: merchant, lines (item ids or item specs), quantities, mandate id. The slot is not a binding field: the backend picks it. The agent's free-text description and reasons are not compared, so a reworded retry returns the stored decision instead of a conflict. The profile version is not compared either: the decision records the version current when it was made, and a redelivery after a profile edit still returns that decision.
- A watch run derives its `purchaseKey` from the mandate id, the run id (in the run token) and the merchant's offer id: a retried run repeats the same proposal and gets the stored decision; the next run gets a new key and a fresh decision on that day's price; the purchase count (section 1) keeps two offers from both buying.
- Different binding fields with the same key is a conflict (a named error), and nothing is decided.

## 6. The replay unit (invariant 8)
Each decision stores its complete **`DecisionInput`** in the audit record. Replaying means `decide(storedInput)` in `control-core`, with no adapter, model or network call. It must return the stored decision. The stored input holds:
- the mandate version (its full content, not only its id) and the agent instance's status;
- the proposal's binding fields and the agent's untrusted text; the **profile version id** current at the decision (never the personal data);
- for an item spec: every candidate offer the merchant returned and the pick rule's choice;
- the merchant directory answer (reachable, networks, protocol) and the **route**: the selected card and network, the mandate's allowed cards, the user's preference order and the merchant's paths that the selection was made from;
- the cart evidence as received: the tier-1 session cart or connector quote, tier-2 product records, and the session's, quote's or slot hold's expiry; for a connector: the manifest version, the slot candidates and the pick, the ceiling's components (quote total, each weighed line's price and allowance, fees, deposits, and whether the quote included them), the connector's state and the toggle, whether the account was linked and named by the mandate, the payment method on file as read against the enrolled agent card, the one-line-mode setting in force, whether the item list was merchant-derived and the completed purchases under this mandate version;
- the intent model's questions, answers, scores, model id and version, or "no answer" with the reason;
- the risk signals and their sources;
- the ledger snapshot used: budget-period totals, the mandate's reserved and counted purchase count, and the earlier purchases the duplicate, split and velocity checks looked at;
- the FX rates used, with their source and timestamp, the foreign-currency margin applied and the mandate's comparison operator;
- the clock reading;
- the settings in force (thresholds, "when unsure" default, the step-up expiry ceiling and margin) and the step-up expiry computed for this decision.

A step-up re-check stores its own `DecisionInput` (section 2). A replay test runs over every stored decision in CI against the labelled cases.

## 7. Event delivery
Spring Modulith's event publication registry delivers each event **at least once, in no guaranteed order**, to in-process listeners. Every listener:
- is idempotent (keyed on the event id, or on the `purchaseKey` and attempt id);
- tolerates reordering. For example, `activity` may see `CheckoutFailed` before `DecisionMade` and must still end in the right state, so it applies state by the aggregate's version, not by arrival order.

When a broker is added later, the relay publishes from the same registry, and these rules don't change.

## 8. What the hook responder keeps in memory
The set of enrolled token references and the bank's `settings` are cached from the published views and refreshed every few seconds (the bound is a setting). On a database outage the responder answers from this cache with the bank's configured fallback; the staleness bound is therefore the longest a just-stopped agent's token can still be treated as enrolled.

## 9. Runs of standing mandates
- `agents` materialises each due run as a row (mandate id, due time) from the confirmed frequency, on the `Clock` port; for a standing mandate whose item list was derived from a merchant account, the first run is due at confirmation, so the STEP_UP it produces reaches the user while they are in the app. A claim is `UPDATE … SET claimed_at, run_id WHERE claimed_at IS NULL`; zero rows updated means already claimed, which is answered as "nothing due" and recorded as a duplicate claim. A claimed run that proposes nothing within its window simply ends; the next due run is independent.
- The run token is minted inside the claim's transaction and is not stored; its verification is by signature and expiry. The run id is in its claims, so a proposal can be tied to the run that made it.
- `MandateFulfilled`, `MandateRevoked` and `AgentStopped` delete or mark the mandate's unclaimed runs in their listener; a claim that races one of them still yields a token, and the proposal it makes is declined on the mandate's or agent's status, which is in the `DecisionInput`.
- Runs and proposals per mandate and per day are counted in `agents` and `decisioning` against `settings`; exceeding a cap is a named error, recorded, and never silent.

## Required tests
- Two concurrent proposals that each fit the budget, but not together: exactly one APPROVE, the other DECLINE or STEP_UP. The same for a proposal racing a step-up approval, and for two mandates racing on the global budget.
- Two runs of a one-shot watch proposing two different offers at once: exactly one APPROVE; the mandate is fulfilled once the attempt completes; a failed attempt releases its reserved count and the next run may buy.
- Two claims of the same due run: one token, the second answered "nothing due" and recorded.
- A proposal carrying personal data in a line: rejected before any merchant call.
- An item spec matching zero, one and several offers; the pick rule's result replays from the stored candidates.
- The step-up expiry per decision: a session shorter than the ceiling in settings shortens the expiry; an approval after it is DECLINE "propose again".
- A fare in a foreign currency at the limit, one cent over, and inside the margin, under `<` and under `≤`: the margin can only turn APPROVE into DECLINE, never the reverse; a negative margin rate is refused by `settings` validation.
- Removing the cardholder's last channel suspends the due runs of their standing mandates; adding one resumes them.
- The `ceiling` function: at the limit, one cent over, a quote total under the limit while the ceiling is over (the ceiling decides), a weighed line in a foreign currency, fees inside and outside the quote; half-even at each step; the components replay from the stored input.
- A proposal at a connector merchant that is suspended, not enabled, turned off for this instance, not linked, or linked but not named by the mandate: DECLINE with the matching reason and no merchant call that uses the user's token. A minimum order at and one cent under. No slot inside the window.
- The card on file is not the agent card: DECLINE with the one-line mode disallowed, APPROVE possible with it allowed, and the mode recorded in the input. The card changed between the decision and `COMPLETING`: `FAILED` "card on file changed", reservation and pre-approval released, no order call.
- An ended link's resolution-only use ends at the latest at slot end plus the reconciliation window, even with a case open; the token is then gone and the case runs on the stored record.
- A merchant's tool hash that differs from the manifest, at session start and on `tools/list_changed`: the connector is suspended, toggles and links are untouched, the next proposal is DECLINE, and re-approval restores service with no user action.
- A discovery argument over its cap is refused at the gateway; injected text in a discovery result and in a stored tool description is data and raises nothing but the suspicious-text flag on the proposal that cites it.
- A connector order that times out and is later found by its order reference: `COMPLETED`, counted once; the same timeout with nothing found by the window's end: `FAILED`, released; a lookup that is impossible: `UNKNOWN` plus a mismatch case. The order call was made exactly once, also under a duplicate `DecisionMade` delivery.
- A link ended while an attempt is `COMPLETING` or `UNKNOWN`: the resolution-only use resolves it; the token is never used for a quote or an order after the end, and never for another cardholder.
- Turning a connector off for one instance leaves the link and a second instance's use of it intact; the bank disabling the connector ends every link to it and cancels attempts in `CREATED`.
- A pending step-up at a connector merchant counts nothing until approved.
- The first run of a merchant-derived list is STEP_UP by its own reason code even under "decline and tell me"; the second run, after a completed purchase, is not.
- An OAuth callback with a wrong `state` or a wrong issuer is refused and recorded.
- The harness configured with a third server address fails to start; a tool result or conversation naming a server changes nothing; an egress attempt outside the policy is refused and logged.
- A crash at each checkout-attempt transition: never two credential requests, and the final state is correct.
- A completion timeout followed by a matching authorisation: `COMPLETED`, and the reservation counted. A completion timeout with no authorisation until the window ends: `FAILED`, reservation and pre-approval released.
- Two distinct authorisations against one pre-approval: only the first matches. The same authorisation id twice: the same answer.
- The same `purchaseKey` delivered twice concurrently: one decision, the same answer twice, one proposal row.
- Each step-up outcome (approve, user decline, expiry, revoke, stop) writes a second decision with its audit row and event.
- A replay of every stored decision returns the same outcome and reasons.
- Each listener under duplicate and reordered delivery.
