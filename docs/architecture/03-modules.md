# 03 Module cut

Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md). Aggregates and port contracts are detailed in T-003; this page fixes the boundaries.

## Control backend
Each module is a bounded context with its own schema, a published application API (Java interfaces and records) and events. Nothing reaches into another module's internals or tables. Spring Modulith verifies the boundaries in a test; ArchUnit checks the layering inside each module.

| Module | Features | Owns | Publishes | Uses |
|---|---|---|---|---|
| `identity` | caller identity | cardholder accounts: local accounts (PoC), OIDC mapping (later), operator roles; agent session tokens, agent-instance credentials and per-run agent run tokens (issue and verify) | — | — |
| `profiles` | checkout data, channels | the cardholder's buyer, traveller and delivery entries (name, date of birth, contact, travel document, address) and their notification channels (e-mail, web-push subscription), versioned and insert-only, edited only from the cardholder app, every change audited; other modules hold version ids, never the data | `ProfileChanged` | `identity` |
| `settings` | bank control (N15) | per-deployment and per-bank settings: allowed models and the LLM spend cap per user, the step-up expiry ceiling and session margin, the pre-approval window, the default "when unsure", the foreign-currency margin, hook mode and fallback, kill switch, ingest caps (chat and watch runs apart), proposal rate limits and per-mandate run and proposal caps; every change audited; the harness reads the part that concerns it through the `get_agent_configuration` tool | `SettingsChanged` | `identity` |
| `agents` | M1, V9 runs | agent instances (owner, status, bound mandates, from `MandateConfirmed`, `MandateRevoked` and `MandateFulfilled`), enrolled cards as network token references (from `CardEnrolled`), stop switch; the **due runs** of standing mandates (computed from the confirmed frequency, claimed once each, counted against the caps) | `AgentStopped`, `AgentResumed`, `RunClaimed` | `identity`, `settings`, `Clock` |
| `mandates` | M2, M12, M14, V9 | drafts, versions, confirmation, tighten/loosen diff, revocation; a mandate's purchase count, run frequency, strict-or-inclusive limit, profile entries it may use and "when unsure" policy are versioned facts; the backend's draft validation forces "decline and tell me" on a standing mandate unless the user picks "ask", and requires a reachable notification channel; **fulfilled** when the count is reached (from `CheckoutCompleted`) | `MandateConfirmed`, `MandateRevoked`, `MandateFulfilled` | `identity`, `agents`, `profiles` (the entry and a channel exist) |
| `decisioning` | M3, M5, M18 | proposals, the decision pipeline, rules (including the purchase count and the foreign-currency margin), the caution combiner, the intent check rules, the step-up expiry per decision, step-ups and every one of their outcomes as a second decision (approve with re-check, decline, expiry by a scheduled job on the `Clock` port, revoke, stop) | `DecisionMade` (outcome APPROVE, DECLINE or STEP_UP, with a reason code and, for second decisions, the first decision's id) | `mandates`, `agents` (status), `settings` (configuration in force), `ledger`, `payments` (reachability, route, cart evidence, resolved offers), `authorisation` (pre-approval in the same transaction), `IntentModel`, `RiskSignal`, `FxRates`, `Clock` |
| `ledger` | M4 | reservations, budget periods, the counting rule, the purchase count per mandate, duplicate and split-order lookups | — (changes reach read models through the events of the modules that call it) | `FxRates`, `Clock` |
| `payments` | M7, M8, enrolment | card enrolment with the networks, started by the cardholder app through `payments`' API for an agent instance; the network may answer pending until the cardholder authenticates; `payments` publishes `CardEnrolled` and `agents` stores the reference (no `agents`↔`payments` cycle); **route selection** before the checkout session (the card and network for this merchant, by fixed rules over the mandate's allowed cards, the user's preference order and the merchant's paths; the decision records and re-validates it), the merchant directory (which merchants have an agent checkout path, on which network, and which structured attributes they can settle), **item spec resolution** (searching the merchant's offers for a typed spec; the pick among matches is a pure rule in `control-core`), checkout sessions with their stored expiry, checkout attempts ([06-consistency.md](06-consistency.md) §3) filled with the profile version the mandate names, network instructions compiled from confirmed mandates, credential release, checkout completion, network confirmation | `CardEnrolled`, `CredentialReleased` (never the credential), `CheckoutPendingUser`, `CheckoutOutcomeUnknown`, `CheckoutCompleted`, `CheckoutFailed` (with reason), `CheckoutCancelled` | `profiles` (read by version id), `PaymentNetwork`, `Checkout`, `MerchantCatalogue` ports with VIC and Agent Pay adapters |
| `authorisation` | bank hook | pre-approvals (one per APPROVE: token, amount ceiling, merchant, window, unused), the match rule, processor profiles, reading the hook call log | `PreApprovalMatched`, `UnapprovedAuthorisationSeen` | — |
| `reconciliation` | M9–M11 | clearing records, the three-way match (decision, authorisation, clearing), a cleared billing amount above the mandate's limit in the base currency flagged, mismatch cases with drafted evidence, the record of money that settled without an approval | `MismatchCaseOpened` | `ClearingFeed` port |
| `audit` | M6 | the append-only event store, the stored `DecisionInput` per decision, ingested conversation and agent trace (untrusted, capped), replay, export | — | — |
| `activity` | M13, M15, console | read models for the cardholder app, the console and the chat's read tools, built from events; the state of each standing mandate's watch (last run claimed, last outcome, stalled when a due run goes unclaimed) | — | everyone's events |
| `notifications` | M5, V9 | delivery to the user's channels from `profiles` (server-sent events to the open web app; e-mail or web push in the PoC; the bank's app later) behind a `Notifier` port; telling the user something needs them (step-up raised, the network needs their authentication, mismatch case opened, checkout failed) and, for standing mandates, what happened without them (completed, unknown, failed, declined for an unsettled requirement; routine "above the limit" declines summarised per day) | — | `profiles` (channels), `DecisionMade` (STEP_UP; DECLINE on a standing mandate), `CheckoutPendingUser`, `CheckoutOutcomeUnknown`, `CheckoutCompleted`, `CheckoutFailed`, `MandateFulfilled`, `MismatchCaseOpened` |

### Shared kernel
`Money` (exact decimal, scale 2, half-even, one currency), the `FxRates` port (its adapter and cache are a small `fx` package in `control-backend`, used by `ledger` and `decisioning`), typed ids, `UntrustedText`, `Clock` and `Random` ports, `PurchaseKey`, the tagged network reference (`Instruction | CheckoutSession | McTransaction | …`), the typed `ItemSpec` categories, and the `events` package with the event types more than one module consumes. Small and stable by intent; a change here is reviewed as a change to every module.

### Where transactions end
One `propose_purchase` runs in one transaction once the evidence is gathered: the decision, the ledger reservation (APPROVE only; a STEP_UP counts once approved, invariant 5), the pre-approval, the audit record with the full `DecisionInput` and the outbox rows commit together, under row locks on the budget periods it touches. A step-up approval is a second decision committed the same way. Network and model calls happen **before** the transaction (gathering evidence) or **after** it (checkout, as an event listener driving a persisted checkout attempt). No transaction is held open across a remote call. The mechanics are in [06-consistency.md](06-consistency.md).

### Dependency rules
- `decisioning` depends on the published APIs of `mandates`, `ledger`, `payments` and `authorisation`; nothing depends on `decisioning` except through its events.
- `payments` starts a checkout only from a `DecisionMade` event whose outcome is APPROVE; a step-up approval is such a decision, re-checked by `decisioning` (never by `payments`).
- `profiles` is read by `payments` (by version id, at checkout), by `notifications` (channels) and by `mandates` (the entry and a channel exist at confirmation); nothing else reads personal data, and no event carries it.
- Event types are published from a module's API package. Where two modules consume each other's events (`payments` and `mandates`, `payments` and `decisioning`), the event types live in the shared kernel's `events` package so that Spring Modulith's cycle check stays strict on code dependencies; T-037 confirms the arrangement.
- `agents` computes due runs from `MandateConfirmed` and stops them on `MandateFulfilled`, `MandateRevoked` and its own stop; `identity` mints the run token on a claim. The harness is never called.
- `audit` is written through the `AuditLog` port by every module, inside the module's transaction.
- `activity` only listens; it never writes another module's data.
- No cycles; Spring Modulith's verification fails the build on one.

## Maven layout
```
agentic-wallet/                    (Maven reactor)
├── shared-kernel/                 pure Java, no dependencies
├── control-core/                  pure Java: the domain of every control module, the rules, the combiner,
│                                  the pre-approval match rule, the ports; depends only on shared-kernel
├── control-backend/               Spring Boot + Spring Modulith: application services, REST API,
│                                  JPA persistence adapters, network and merchant adapters, outbox
├── control-api/                   the OpenAPI contract of the backend's REST API (agent and cardholder sides)
├── control-mcp/                   Spring Boot + Spring AI MCP server; no persistence
├── hook-responder/                Spring Boot; depends on control-core for the match rule
├── agent-harness/                 Spring Boot + Spring AI (chat, MCP client)
├── web/                           React + TypeScript
└── simulators/                    vic, agent-pay, processor-hook, clearing-generator, demo-shop,
                                   scripted-agent-driver, benchmark-runner (M16) (test scope)
```
`control-core` has no framework, database, HTTP, clock or random dependency, so the compiler enforces invariant 9; ArchUnit is the second check. `control-core` mirrors the backend's module packages (`…core.mandates`, `…core.decisioning`, …), and because Spring Modulith's verification does not run there, an ArchUnit rule in `control-core` forbids imports between those packages except through the shared kernel. The pure core is shared with `hook-responder`, so the backend and the hook apply exactly the same match rule.

## Agent harness
| Module | Holds |
|---|---|
| `chat` | conversations, streaming to the web app, the model picker |
| `model-gateway` | `ModelGateway` port: our gateway's model list, the bank's gateway when inside its perimeter, a fixture adapter; a hard spend cap per user; no card or token data ever in a prompt |
| `shopping-tools` | search, read page; everything read is untrusted text |
| `mandate-drafting` | the guided conversation that produces a structured draft through `draft_mandate`; it never activates anything |
| `memory` | purchases, merchants, categories, price points, habits as counters in PostgreSQL; ranking input only, never a rule input |
| `trace` | records searches, pages read, candidates and reasons; sends them and the conversation to the ingest endpoint |
| `watch` | the runner for standing mandates: claims due runs with the agent-instance credential, runs the price check without a model where it can (the threshold check in the harness is advisory; the backend decides on the merchant's cart), calls the model only when a candidate appears, proposes with a `purchaseKey` derived from the mandate and the offer, one run at a time per mandate; keeps no state it cannot rebuild from `list_mandates` and the backend's answers |
| `control-client` | the MCP client to `control-mcp`, plus the runtime's ingest and run-claim calls |

## Hook responder
| Part | Holds |
|---|---|
| processor profiles | request and response shapes per processor (Thredd-shaped and Enfuce-shaped first, from public documentation; the bank's own once known), the timeout behaviour the bank configures |
| match | the pure match rule from `control-core`: token, amount at or below the approved one, same merchant, inside the window, unused |
| modes | shadow (record what it would answer; the real answer is "no opinion") and enforcing; scope, currency rule and fallback in [02-system.md](02-system.md) |
| consumption | one authorisation per pre-approval, under a unique constraint ([06-consistency.md](06-consistency.md) §4) |
| call log | every call, its answer and its latency, insert-only, deduplicated by the processor's authorisation id |

## Events (outbox)
| Event | From | Consumed by |
|---|---|---|
| `MandateConfirmed`, `MandateRevoked`, `MandateFulfilled` | mandates | agents (bound mandates, due runs start and stop), payments (compile or cancel the network instruction; on revoke or fulfilment, cancel attempts still in `CREATED`), decisioning (on revoke or fulfilment, decline open step-ups), notifications (`MandateFulfilled`), activity |
| `AgentStopped`, `AgentResumed` | agents | payments (suspend the token, cancel unused instructions and attempts in `CREATED`), decisioning (decline open step-ups), activity |
| `RunClaimed` | agents | activity (watch state) |
| `ProfileChanged` | profiles | activity (version shown, never the data) |
| `DecisionMade` | decisioning (first decisions and every step-up outcome) | payments (checkout on APPROVE), activity, notifications (STEP_UP; DECLINE on a standing mandate) |
| `CardEnrolled` | payments | agents |
| `SettingsChanged` | settings | activity (console) |
| `CredentialReleased`, `CheckoutCompleted`, `CheckoutFailed`, `CheckoutCancelled` | payments | reconciliation, mandates (`CheckoutCompleted` counts toward the purchase count; `CheckoutFailed` and `CheckoutCancelled` reopen it), activity, notifications (`CheckoutCompleted`, `CheckoutFailed`); the reservation and pre-approval release happen in `payments`' transaction through `ledger`'s and `authorisation`'s APIs ([06-consistency.md](06-consistency.md) §3) |
| `CheckoutPendingUser`, `CheckoutOutcomeUnknown` | payments | notifications, activity |
| `PreApprovalMatched`, `UnapprovedAuthorisationSeen` | authorisation (from the hook call log) | reconciliation, activity |
| `MismatchCaseOpened` | reconciliation | activity, notifications |

Every listener is idempotent, keyed on the event id or the `purchaseKey`, and tolerates reordering ([06-consistency.md](06-consistency.md) §7).
