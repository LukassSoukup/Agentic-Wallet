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
