# Feature-source log

Clean-room rule: every feature has a public or own source and a date. Features M1–M18 and N1–N18 were sourced by the earlier strategist handoff (2026-10-17); since 2026-10-29 [PROJECT_BRIEF.md](../../PROJECT_BRIEF.md) defines them. Only additions and changes made here are listed.

| Date | Feature | Change | Source |
|---|---|---|---|
| 2026-10-18 | Global budget across agents | added | the user, for the hackathon demo; public: per-agent and shared spend limits in agent-wallet products (Payman, Nekuda, Skyfire public sites) |
| 2026-10-18 | Payment methods with block | added | the user, for the hackathon demo; public: card freeze in consumer banking apps; network agent programmes in [payment-networks](payment-networks.md) |
| 2026-10-18 | x402 crypto rail (N6) | promoted to the demo | the user, for the hackathon demo; public: the x402 open specification (Coinbase, github.com/coinbase/x402) |
| 2026-10-18 | Hostile text raises to at least STEP_UP | stricter than handoff J6 | the user, for the hackathon demo; invariant 2 |
| 2026-10-23 | x402 crypto rail (N6) | returned to nice-to-have | the user, in the 2026-10-23 refocus |
| 2026-10-29 | V1 chat with the user's chosen LLM, V2 our own shopping agent, V3 memory as ranking input | added: we build the agent | the user's product vision; public: the card networks' agent programmes register agent platforms that enrol cards and shop on the cardholder's behalf (Visa Intelligent Commerce and Mastercard Agent Pay public documentation and rules) |
| 2026-10-29 | M1 agent registry | changed: the entries are our own agent instances per user, with enrolled cards as token references | follows from V2 |
| 2026-10-29 | M7 approved payment only | changed: one single-use network credential per approval, requested by the trusted backend | public: per-purchase credentials in the networks' agent programmes |
| 2026-10-29 | M18 intent check | changed: decided on the merchant's cart from a protocol checkout session; auto-purchase only when fixed rules settle every requirement ([ADR-0007](../adr/ADR-0007-cart-evidence-and-auto-purchase.md)) | the user with the architect; public: UCP and ACP checkout-session schemas |
| 2026-10-29 | V9 price watch | added as a later feature | the user's product vision |
| 2026-10-29 | V4–V8, N10 | dropped | not needed by the product definition |
| 2026-10-31 | V10 merchant connectors | added as a later feature: the curated connector catalogue, per-user on/off, account linking from the app, quotes and orders by the backend, the amount ceiling | the user; [ADR-0008](../adr/ADR-0008-merchant-connectors-and-the-connector-catalogue.md) |
| 2026-11-19 | V2 shopping agent | changed: buys only where a shop's agent checkout accepting our provider's tokens or an admitted merchant connector exists | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | V11 merchant scan | added: reviews and rating of shops new to the user, as ranking and caution only, shown to the user | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M1 agent registry | changed: the user's saved payment methods are provider references, no enrolled cards | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M7 approved payment only | changed: one provider token per approval, bound to shop, amount, currency and expiry, revoked or switched off after use; our provider key cannot charge | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M8 routing | changed: which saved payment method pays, chosen by fixed rules within what the user allowed | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M9 match | changed: decision, the provider's token events and the shop's order record, no clearing files | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M10 mismatch detection | changed: only where we can see it (different amount, used twice, an order with no decision, a missing confirmation) | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | M17 data behind adapters | changed: a payment provider simulator and a demo shop replace the network and processor simulators | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | N15 operator console | changed: the operator's console (admitted connectors, kill switch, rates and latency) replaces the bank operations console | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | N17 3-D Secure in the app | changed: 3-D Secure confirmations through the provider's own window, needed in v1 (was a later passkey or 3-D Secure option for asking); passkeys later | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | N18 languages | changed: English first, then Czech and other EU languages, accessibility (was a Czech and English UI) | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | N20 bank channel | added as a later feature: the app under a bank's brand, with the bank's own authorisation check as a third line | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | V9 price watch | changed: a 3-D Secure request while the user is away lets the purchase lapse and tells the user | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | V10 merchant connectors | changed: the bank-issued agent card removed; banks later | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
| 2026-11-19 | N19 spending insights | changed: from the bank's transaction history to the user's own bank data | the user (T-042); [payments-for-a-standalone-app](payments-for-a-standalone-app.md) |
