# 04 One purchase, end to end

The cart and auto-purchase rules are decided in [ADR-0007](../adr/ADR-0007-cart-evidence-and-auto-purchase.md); the deployables and modules in [02-system.md](02-system.md) and [03-modules.md](03-modules.md).

## Sequence
```
user      web     harness+LLM     control-mcp     control-backend                         merchant / network
 │ "buy …" │ ───────▶ │                                                                    
 │         │          │ search, read pages (untrusted), rank with memory                   
 │         │          │ propose_purchase ─▶ │ validate, type text as untrusted              
 │         │          │                     │ ─REST─▶ decisioning: purchaseKey seen? → same answer
 │         │          │                     │         payments: merchant reachable? ──────▶ registry
 │         │          │                     │         payments: open checkout session ────▶ UCP/ACP session
 │         │          │                     │                   ◀── priced cart (evidence tier 1)
 │         │          │                     │         payments: product records ──────────▶ catalogue (tier 2)
 │         │          │                     │         intent model: typed questions ──────▶ extraction model
 │         │          │                     │         control-core: decide(input)  (pure)
 │         │          │                     │         ONE TRANSACTION: decision, reservation, pre-approval,
 │         │          │                     │                          audit record, outbox rows
 │         │          │ ◀── outcome + reasons
 │ STEP_UP: question ◀── backend (event) ; answer ──▶ backend directly (never via MCP)
 │                                          backend: re-check → APPROVE or DECLINE
 │                                          payments (event listener after APPROVE):
 │                                            request credential for exact amount + merchant ──▶ VIC / Agent Pay
 │                                            complete checkout with it (same idempotency key) ─▶ merchant
 │                                            confirm to network with the decision id ─────────▶ network
 │                                          credential discarded; "released" event recorded
 │                                                   merchant's PSP authorises ─▶ network ─▶ bank processor
 │                                                   bank processor ─▶ hook-responder: match pre-approval
 │                                          reconciliation: decision ↔ authorisation ↔ clearing
```

## Steps
1. **Shop (harness).** The agent searches and reads merchant pages as untrusted text, ranks with the user's memory, and picks merchant, items and quantities. Memory may lead to a question, never a permission.
2. **Propose (control-mcp).** `propose_purchase` carries the `purchaseKey` (the harness generates it once per intended purchase and reuses it on every retry), the merchant reference, item ids and quantities, and the agent's own description and reasons. The server validates the shape, wraps every free-text field as `UntrustedText`, attaches the caller's identity from the session token and forwards it to the backend.
3. **Idempotency.** The key is (agent instance, `purchaseKey`). Equal binding fields (merchant, items, quantities, mandate) return the stored decision; different binding fields are a conflict (invariant 7). The agent's free text is not compared ([06-consistency.md](06-consistency.md) §5).
4. **Reachability and route.** `payments` looks the merchant up in its directory (built from the networks' merchant registries and, in the PoC, the demo shop). No agent checkout path on any of the user's allowed networks means DECLINE with the reason "this merchant can't be bought from by an agent". Otherwise `payments` selects the **route** (card and network) by fixed rules from the mandate's allowed cards, the user's preference order and the merchant's paths, because the checkout session is opened on one network's path. The decision (step 7) records that route with its reason and re-validates it; it can decline the route, never switch it. A card change is a new proposal.
5. **Gather cart evidence.** If the merchant supports protocol checkout sessions (UCP or ACP through Visa's gateway; a Mastercard Level 3 merchant API, simulated), `payments` opens the session with the item ids and quantities and receives the merchant's priced cart. It also fetches the merchant's product records where the merchant publishes them. See the tiers below.
6. **Extract facts (outside the core).** Mandate requirements are stored as structured facts (attribute, operator, value) since the confirm step. For each requirement that structured cart data cannot settle, fixed code derives one typed question ("is the size 42?", "is it leather?") and asks the intent model about the merchant's product text. The answers and their scores are stored as decision inputs. A failed or slow call means no answer.
7. **Decide (pure core).** `decide(DecisionInput) → Decision`. Order: hard rules (amount, budget, merchant, category, quantity, timing; over a limit is DECLINE), the intent check (matches, deviates or uncertain, per ADR-0007), ledger checks (duplicate, split order, velocity), routing feasibility, then the caution combiner, where the outcome only rises: APPROVE < STEP_UP < DECLINE. A risk signal or a model finding can only raise it. Every decision carries its reasons.
8. **Commit.** One transaction, under row locks on the budget periods it touches: the decision, the ledger reservation (APPROVE only), the pre-approval for the bank's hook (APPROVE only), the audit record with the complete `DecisionInput` (the replay unit), and the outbox rows ([06-consistency.md](06-consistency.md) §1, §6).
9. **Step-up (M5).** The cardholder app shows one specific question with the merchant's cart, the reasons and an approve/decline answer, with an expiry from the configuration. The answer goes from the app to the backend, authenticated as the user. On approve, `decisioning` makes a **second decision** in one transaction: same mandate version, the ledger still allows it under the budget locks, the checkout session's stored expiry has not passed (no merchant call inside the transaction; an expired session means DECLINE with "propose again"). On APPROVE it reserves, writes the pre-approval and stores its own `DecisionInput` ([06-consistency.md](06-consistency.md) §2). Expiry is enforced by a scheduled job in `decisioning`. Expiry or decline releases nothing, because nothing was counted.
10. **Checkout (payments, after APPROVE).** An outbox listener drives a persisted **checkout attempt** ([06-consistency.md](06-consistency.md) §3): it requests one credential for exactly this amount, merchant and line items (VIC `/credentials` with the mandate reference; the Agent Pay token and cryptogram in the simulator), completes the merchant's checkout session with it using an idempotency key derived from the `purchaseKey`, and sends the network confirmation with the decision id as its reference. The credential lives only in memory for that call and is never stored or logged; only the event "credential released" is recorded.
11. **Authorise and hook.** The merchant's payment provider authorises through the network to the issuing bank. Where the bank points its processor at us, the hook responder matches the authorisation to the pre-approval (token, transaction amount at or below in the same currency, same merchant, inside the window, not yet consumed), consumes it under a unique constraint, answers within budget and logs the processor's id ([06-consistency.md](06-consistency.md) §4). In the PoC, the processor hook simulator does this in shadow mode.
12. **Reconcile.** Decision, authorisation (from the network confirmation or the hook log) and clearing (the bank's feed; synthetic files in the PoC) are matched. A mismatch opens a case with drafted evidence. Money that settled without an approval (network stand-in, over-capture, force post) is kept in its own record and flagged; it never counts toward or against the user's limits silently.

## Cart evidence tiers
| Tier | Source | What it can establish |
|---|---|---|
| 1 | The merchant's priced cart returned through a protocol checkout session, fetched by the backend | binding facts: merchant, item ids, quantities, unit prices, total, currency; structured attributes the merchant sends |
| 2 | Product records the backend fetched from the merchant's own catalogue or feed | descriptive facts; structured fields such as GTIN, size or category, by fixed rules |
| 3 | What the agent says (its description, reasons, the pages it read) | nothing positive; a contradiction with tiers 1 and 2 raises the outcome to at least STEP_UP |

**Auto-purchase (APPROVE without asking) needs tier 1, and every requirement in the mandate settled by fixed rules over tier 1 or tier 2 structured data.** A tier-1 cart that leaves a requirement open is at most STEP_UP, and the user decides on that cart's binding total. **No tier-1 cart means DECLINE "can't buy here"**: nothing binding exists to approve or to pay. A broken hard limit is DECLINE whatever the evidence. The intent model can find a problem but never clear one, because invariant 2 allows a model to make a decision more cautious, never less. So mandates that name exact products, categories or price ranges can run on their own, and free-text quality requirements ("leather, size 42" when the merchant sends only a title) go to the user.

The **merchant identity** the decision approves (the registry's merchant id and name) must be mapped to what the hook and the clearing see (acquirer merchant id, descriptor, category code). That mapping is part of the merchant directory and is measured in shadow mode before enforcing; until a bank's data exists it is an open item ([05-open-items.md](05-open-items.md)).

Where this works today: only at merchants that run a protocol checkout session and return the cart. That is a small set (US-first; a handful named in Europe; none known in Czechia), and in the PoC it is our demo shop. Whether Visa's gateway relays populated line items and totals is known from the published SDK models only, not from a live call; T-034 checks it.

## Failure behaviour
| Situation | Behaviour |
|---|---|
| The merchant offers no agent checkout session, can't open one, or returns an error | DECLINE "can't buy here" (reason: no session). A session that opened but left requirements unsettled is a STEP_UP with that cart |
| The intent model times out, errors or exceeds its spend cap | no answer → the requirement stays unsettled → uncertain → the mandate's "when unsure" (ask or decline), never approve |
| The network answers "pending cardholder authentication" | two independent gates: our decision stands and the reservation counts; the user finishes the network step in the app; any outcome other than success releases the reservation |
| The credential was released but checkout fails | the attempt goes to `FAILED`: release the reservation, cancel the pre-approval, cancel the network instruction where allowed; the credential expires unused; a retry with the same `purchaseKey` gets the stored decision, and a new credential needs a new decision |
| The completion call times out | the attempt is `UNKNOWN`, never `FAILED`: reservation and pre-approval stay; a resolver reads the session status; a matching authorisation makes it `COMPLETED`, the window ending without one makes it `FAILED` |
| The process dies between requesting the credential and completing | on restart the attempt is resolved from the session status; a second credential is never requested for the same decision |
| Two proposals race for the last of a budget | budget-period row locks: exactly one reserves; the other is decided on the updated ledger |
| The merchant changes the price or adds items at completion | the amount or items no longer match the decision; the attempt goes to `FAILED` with the reason "cart changed" and the new cart is a new proposal |
| The same request arrives twice (proposal, event, hook call) | deduplicated by `purchaseKey`, event id or the processor's authorisation id |
| The harness or the LLM is down | nothing is proposed; the backend, the hook responder and the cardholder app keep working |
| Hostile text in a product page or the agent's reasons | stored as data; the suspicious-text flag raises the outcome to at least STEP_UP |
