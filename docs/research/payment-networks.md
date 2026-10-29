# Agentic payment networks: what to sandbox

Source: the user's own analysis project `~/IdeaProjects/leash-analysis` (ANA-LEASH-004, -011, -012 and the "agentic payments landscape" page, research dated 2026-10-01 to 2026-10-05). Only the facts about public networks and standards are taken from it. Nothing about the hackathon's design. Re-verify before relying on any of it in production.

## The two networks
| | Visa Intelligent Commerce (VIC) | Mastercard Agent Pay + Verifiable Intent (VI) |
|---|---|---|
| Agent credential | Agent-specific token from Visa Token Service | Agentic Token (MDES token bound to the agent) |
| Customer intent | **Payment Instruction** (`POST /acp/v1/instructions`): `mandates[]` with `description`, `declineThreshold` (amount + currency), `effectiveUntilTime`; optional `quantity`, `preferredMerchantName`, recurrence. **No line items.** | **VI 3-layer SD-JWT**: L1 issuer (~1 yr), L2 user mandate with constraints (amount range, payees/merchants, line items **by ID**, budget, recurrence), L3 agent (~5 min), split into a payment mandate (to the network) and a checkout mandate (to the merchant) |
| Customer confirmation | Visa Payment Passkey | Mastercard Payment Passkey; 3DS fallback |
| Agent identity | Trusted Agent Protocol (merchant verifies signed requests) | Know Your Agent registration; Web Bot Auth |
| Network checks | Credential request and authorisation must match the instruction | Chain verified "as a pre-authorization step"; the final decision is the issuer's |
| Sandbox | **Self-serve, free** at developer.visa.com (REST APIs, MCP server, samples) | Sandbox keys via a developer.mastercard.com project, but **Agent Pay integrators must register with Mastercard** (not self-serve). MCP toolkit: `Mastercard/developers-agent-toolkit` |

Related open standards: AP2 (Google, now at FIDO; intent/cart/payment mandates), Verifiable Intent (draft v0.1, Apache-2.0), EMVCo agentic framework (draft), ACP + Stripe Shared Payment Tokens. AP2, VI and AMP all use 3-layer SD-JWT mandate chains, so **one engine with thin adapters can read all of them**.

## Gaps the networks leave (our product space)
- No network judges whether the basket matches what the user meant. Item attributes are informational, return terms and merchant text aren't checked, and there is no familiarity or duplicate detection across agents.
- No public source confirms that the issuer sees the mandate or the basket at authorisation. → The control layer needs the **basket before payment**, from the agent side.

## Integration points to model
1. **Pre-payment check** (primary): the agent calls us with the basket before credential retrieval / L3 signing → APPROVE / DECLINE / STEP_UP.
2. **Mandate capture**: compile user rules. Where possible, *also* express amount/merchant/expiry/budget as network constraints (VIC instruction, VI L2), so the network enforces them too. Keep item and terms rules in our layer.
3. **Authorisation re-check**: fast and deterministic (amount, merchant, ledger, duplicates). Match it to the earlier basket decision by reference. Real issuer hooks have budgets of 200 ms to 3 s.
4. **Step-up**: model it as a passkey/3DS-style challenge, not an invented mechanism.
5. **Reconciliation/disputes**: the audit log is the evidence record.

## Sandbox approach (user decision 2026-10-14: networks must be sandboxed)
- A `PaymentNetwork` port with **simulated adapters** for VIC and Mastercard/VI, built from the public specs (payload shapes, token, instruction/mandate, auth, settlement files). Use these in all tests and the default runtime.
- Real sandbox adapters come later behind the same port: Visa first (self-serve). Mastercard after registration. Both need the user's own sign-ups.
- Contract tests run against both the simulator and the real sandbox, so the simulator can't drift from reality.
- Reference code to study (not to vendor): AP2 samples, the Verifiable Intent reference implementation.

Tracked in T-005.

Visa details, sandbox access and what we've learned from live calls: [visa-intelligent-commerce](visa-intelligent-commerce/visa-intelligent-commerce.md). Note (2026-10-23): the Cybersource route needs a token requester ID and relationship ID from an account manager, so "self-serve" above may not fully hold; see [vic-open-questions](visa-intelligent-commerce/vic-open-questions.md).
