---
id: ADR-0007
status: proposed
date: 2026-10-29
tasks: ["T-033"]
---
# ADR-0007: Cart evidence and when a purchase may go ahead without asking

## Context
The intent check (M18) compares the cart with what the user meant. Neither card network gives anyone the merchant's cart: the issuer sees amount, merchant, category and token only, and the network-side intent objects carry totals and merchant, not checked item details. The agent builds the cart, but the agent is ours and untrusted (it reads hostile shop text), so its own description of the cart cannot be what approves it. A structured, priced cart exists only where the merchant runs an agent checkout session and replies with one: UCP or ACP sessions relayed by Visa's agent checkout gateway (line items and totals exist in the published SDK models; no live response seen yet), or a Mastercard Level 3 merchant API. Few merchants do this today, none known in Czechia. Checkout happens only through the networks' agent protocols, never by filling a web form, so Mastercard Level 2 (a normal form with a registered merchant) is out of scope. Invariant 2: a model may only make a decision more cautious. [ADR-0003](ADR-0003-model-use-caution-only.md) already says a fact settled only by a model can lead to "deviates" or "uncertain", never to "matches". Agreed with the user on 2026-10-29. **Partly superseded on 2026-10-31 by [ADR-0008](ADR-0008-merchant-connectors-and-the-connector-catalogue.md):** a priced quote the backend obtains from an admitted merchant connector is a second source of tier-1 evidence, so "no checkout session, no purchase" reads "no binding cart or quote, no purchase", and auto-purchase through a connector further requires an order bound to the quote. The tiers and the model's role are unchanged.

## Options considered
1. **Ranked evidence, fetched by the trusted backend; auto-purchase only on a merchant-returned cart settled by fixed rules (chosen).**
2. Decide on the cart and text the agent sends: simple, but a compromised agent can describe its way to an approval.
3. Let a high-confidence model match count as "matches" on a merchant-returned cart: fewer questions to the user, but it breaks invariant 2. Rejected by the user.

## Decision
- **Evidence tiers.** Tier 1: the merchant's priced cart from a protocol checkout session, opened by `payments` in the backend with the agent's item ids and quantities (since ADR-0008 also the priced quote `payments` obtains from an admitted merchant connector). Tier 2: product records the backend fetches from the merchant's own catalogue or feed. Tier 3: anything from the agent (description, reasons, pages read). Binding facts (merchant, items, quantities, prices, total, currency) come from tier 1 only.
- **Auto-purchase rule.** APPROVE without asking requires tier-1 evidence **and** every mandate requirement settled by fixed rules over tier-1 or tier-2 structured data. A tier-1 cart whose structured data leaves a requirement open gives at most STEP_UP: the user sees that cart and its binding total and decides. A broken hard limit is DECLINE whatever the evidence.
- **No binding cart or quote, no purchase** (heading widened by ADR-0008; originally "no checkout session, no purchase"). A merchant that offers no agent checkout session and, since ADR-0008, no admitted ordering connector gives DECLINE with the reason "can't buy here": without a session or quote there is no binding amount, nothing to show the user, and no path to pay. The agent's own cart text is never made binding by a user's answer.
- **Agent text** can only raise the outcome: a contradiction between tier 3 and tiers 1–2, or hostile text, gives at least STEP_UP.
- **The intent model** answers fixed, typed questions about tier-1 and tier-2 text, outside the pure core, behind an `IntentModel` port. A confident "doesn't match" can raise the outcome; no answer can lower it; a failure, timeout or spend-cap hit is no answer. Its inputs and outputs are stored in the decision record, and replay never calls it. It is never called on the hook path. Candidate adapter: Jev (TypeSafe), subject to T-036; a fixture adapter for tests.
- **Step-up shows the merchant's cart** (tier 1 when present) with the reasons; the credential is requested for exactly the approved amount and merchant.
- **Mastercard Level 2 is out.** Mastercard is kept through Agent Pay Level 3 and Verifiable Intent, simulated until sandbox access exists (T-035).

## Consequences
- \+ A compromised or confused agent can at most cause a question to the user, never an approval.
- \+ The rule is testable per tier, with edges: no session, empty line items, title-only items, model failure, hostile text, agent contradiction.
- − Purchases of any kind work only at merchants with protocol checkout sessions; within them, free-text requirements ask the user. That matches the brief's accepted trade-off: unclear cases go back to the user more often, and reach grows with the networks' merchant programmes.
- − Free-text quality requirements ("leather, size 42") are asked about unless the merchant sends them as structured data.
- − Depends on the gateway returning populated carts; T-034 checks it before T-005 relies on it.
- Amends [ADR-0003](ADR-0003-model-use-caution-only.md): the model runs in the backend outside the pure core, on merchant-sourced text, and the provider is chosen per T-036 instead of being OpenRouter by default.
