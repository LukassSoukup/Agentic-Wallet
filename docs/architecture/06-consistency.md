# 06 Consistency, concurrency and replay

The mechanics that keep money right when things run at the same time, fail half-way or arrive twice. Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md). Every mechanism here has a required test (see the end).

## 1. Budget reservations under concurrency (invariant 5)
Two proposals, or a proposal and a step-up approval, can arrive at the same moment against the same budget. Both would read "enough left" and both would reserve.

- The ledger keeps one **budget-period row** per (mandate budget, period) and one per (global budget, period). It holds the reserved and counted totals in the base currency.
- Locking is done by `ledger`'s published API, which `decisioning` calls at the start of the decision's transaction; `decisioning` never touches ledger tables. **Every first decision and every step-up approval** locks every budget-period row it touches (a first decision does not yet know whether it will reserve) with `SELECT … FOR UPDATE` **at the start of its transaction**, in a fixed order (global first, then by mandate id) so two transactions can't deadlock. Then it reads the remaining amount, decides and writes, all under that lock.
- Isolation level `READ COMMITTED` plus these explicit row locks. No `SERIALIZABLE` retries.
- Rows for a period that doesn't exist yet are created with `INSERT … ON CONFLICT DO NOTHING` before they are locked.
- The duplicate, split-order and velocity checks read the ledger under the same locks, so they see every committed reservation.
- **What counts:** remaining = budget − reserved − counted. A reservation is made at APPROVE for the approved amount in the base currency at the decision's FX rate; it turns into *counted* when the checkout attempt reaches `COMPLETED`, at the same amount (the authorised and cleared amounts are reconciliation's business, not the ledger's); it is released on `FAILED` or `CANCELLED`. The exact counting rule with its edges is written in T-003 from this.

## 2. Every step-up outcome is a second decision
A STEP_UP ends in exactly one of: the user approves, the user declines, the question expires (the scheduled job in `decisioning`), the mandate is revoked, the agent is stopped. **Each of these is a new decision record** in `decisioning`, linked to the first, with its own `DecisionInput`, its reason code, its audit row and its `DecisionMade` outbox row, committed in one transaction. A user's decline, an expiry, a revocation or a stop produce DECLINE with the corresponding reason and lock nothing. An approval runs the re-check:
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
- **A timeout is `UNKNOWN`, never `FAILED`.** While an attempt is `UNKNOWN`, the reservation and the pre-approval stay in force. A resolver job queries the session status. If the hook or reconciliation sees an authorisation that matches, the attempt is `COMPLETED`. If the pre-approval window ends without one, it is `FAILED`.
- **`PENDING_USER`:** the network answered that the cardholder must authenticate (its own passkey or app step). The reservation and the pre-approval stay; the `CheckoutPendingUser` event lets `notifications` tell the user; the attempt continues when the network reports success, and goes to `FAILED` when the network's pending state ends without it or the pre-approval window closes.
- **`CANCELLED`:** a mandate revocation or an agent stop reaches an attempt that has not yet requested a credential. The reservation and the pre-approval are released; the event is `CheckoutCancelled`. An attempt that has already requested its credential runs to its end: the user approved that purchase under a mandate that was valid at the time, and the money may already be committed at the merchant.
- **On `FAILED` or `CANCELLED`:** `payments`' application service calls the published APIs `ledger.release(decisionId)` and `authorisation.cancel(decisionId)` **in its own transaction**, together with the attempt's state change and the `CheckoutFailed` (with its reason) or `CheckoutCancelled` outbox row, so the three facts change atomically. Events are for read models and reconciliation, never for keeping these three consistent. Afterwards `payments` cancels the network instruction where the network allows it (only before its credentials are used).
- Idempotency keys toward the merchant and the network come from the `purchaseKey` and the attempt id, so a retried call is the same call.

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
- "Same request" means the **binding fields** are equal: merchant, item ids, quantities, mandate id. The agent's free-text description and reasons are not compared, so a reworded retry returns the stored decision instead of a conflict.
- Different binding fields with the same key is a conflict (a named error), and nothing is decided.

## 6. The replay unit (invariant 8)
Each decision stores its complete **`DecisionInput`** in the audit record. Replaying means `decide(storedInput)` in `control-core`, with no adapter, model or network call. It must return the stored decision. The stored input holds:
- the mandate version (its full content, not only its id) and the agent instance's status;
- the proposal's binding fields and the agent's untrusted text;
- the merchant directory answer (reachable, networks, protocol) and the **route**: the selected card and network, the mandate's allowed cards, the user's preference order and the merchant's paths that the selection was made from;
- the cart evidence as received: the tier-1 session cart, tier-2 product records, and the session's expiry;
- the intent model's questions, answers, scores, model id and version, or "no answer" with the reason;
- the risk signals and their sources;
- the ledger snapshot used: budget-period totals and the earlier purchases the duplicate, split and velocity checks looked at;
- the FX rates used, with their source and timestamp;
- the clock reading;
- the configuration in force (thresholds, "when unsure" default, step-up expiry).

A step-up re-check stores its own `DecisionInput` (section 2). A replay test runs over every stored decision in CI against the labelled cases.

## 7. Event delivery
Spring Modulith's event publication registry delivers each event **at least once, in no guaranteed order**, to in-process listeners. Every listener:
- is idempotent (keyed on the event id, or on the `purchaseKey` and attempt id);
- tolerates reordering. For example, `activity` may see `CheckoutFailed` before `DecisionMade` and must still end in the right state, so it applies state by the aggregate's version, not by arrival order.

When a broker is added later, the relay publishes from the same registry, and these rules don't change.

## 8. What the hook responder keeps in memory
The set of enrolled token references and the bank's `settings` are cached from the published views and refreshed every few seconds (the bound is a setting). On a database outage the responder answers from this cache with the bank's configured fallback; the staleness bound is therefore the longest a just-stopped agent's token can still be treated as enrolled.

## Required tests
- Two concurrent proposals that each fit the budget, but not together: exactly one APPROVE, the other DECLINE or STEP_UP. The same for a proposal racing a step-up approval, and for two mandates racing on the global budget.
- A crash at each checkout-attempt transition: never two credential requests, and the final state is correct.
- A completion timeout followed by a matching authorisation: `COMPLETED`, and the reservation counted. A completion timeout with no authorisation until the window ends: `FAILED`, reservation and pre-approval released.
- Two distinct authorisations against one pre-approval: only the first matches. The same authorisation id twice: the same answer.
- The same `purchaseKey` delivered twice concurrently: one decision, the same answer twice, one proposal row.
- Each step-up outcome (approve, user decline, expiry, revoke, stop) writes a second decision with its audit row and event.
- A replay of every stored decision returns the same outcome and reasons.
- Each listener under duplicate and reordered delivery.
