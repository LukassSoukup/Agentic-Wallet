# Backlog

One list, top to bottom in order. Each item becomes one branch and one PR; delete it from here in that PR when it's done. Ids are `T-NNN`, numbered in order of creation and never reused; the branch is `t-NNN-<slug>` and each commit starts with `T-NNN: `. **Next free id: T-033** (raise it in the PR that adds an item). Finished items live on in git history and PR titles. T-001 (clean-code and design-patterns skills) shipped before this numbering, as `TASK-075` in its commits and PR #1; `main` is not rewritten. T-002 is the task and ADR renumbering.

## Now: planning (no production code until these are agreed)

### T-003 Architecture and domain model
Brief step 3. Supersedes [ADR-0002](adr/ADR-0002-domain-model-and-ports.md) with its own ADR, plus concept notes in `docs/domain/`.
- **Goal:** context/module diagram; aggregates (agents, mandates, purchases, decisions, payments, settlement, refunds, reconciliation) and their invariants; inbound and outbound ports; the decision pipeline (fixed rules, a risk signal that can only add caution, step-up); the audit log and replay design; the idempotency approach.
- **Must add** (from the refocus, 2026-10-23): an owner (cardholder) id on every aggregate and audit record; a caller-identity inbound port and the **auth mechanism** (open, owned here); Settlement and Refund aggregates; `RiskSignal` vs `IntentModel`; `ScenarioSource` as a test adapter, not a core port.
- **Keeps from ADR-0002:** pure core with ports; the flow order; the caution combiner; agent identity from the credential, never from the body.
- Persistence follows T-004.

### T-004 Stack revision (supersedes ADR-0001)
[ADR-0001](adr/ADR-0001-tech-stack.md) was decided for a 26-hour hackathon.
- **Keep on merit:** Java 25, pure core checked by ArchUnit, Money/BigDecimal, PostgreSQL, Flyway, insert-only audit by DB role and trigger, idempotency key, the test stack, React + TypeScript.
- **Re-decide:** persistence (Hibernate/JPA is allowed again (user, 2026-10-23), argued against the insert-only audit and the pure core); run target (multi-arch `docker compose` locally, deployment is a later epic); quality tooling (picked in T-008).
- **Already decided:** one LLM provider (OpenRouter, model id configurable, plus a fixture adapter, spend cap; no Bedrock); x402 and its Node wallet side leave the stack; generated UI is a reference only.

### T-005 Payment network sandbox design (supersedes ADR-0004)
- **Gate:** the user walks through the Visa Intelligent Commerce sandbox first (registered 2026-10-23). Notes: [visa-intelligent-commerce](research/visa-intelligent-commerce/visa-intelligent-commerce.md), open questions in [vic-open-questions](research/visa-intelligent-commerce/vic-open-questions.md).
- **Goal:** an ADR for the `PaymentNetwork` port: simulators for VIC and Mastercard Agent Pay / Verifiable Intent built from the public specs (VIC, Trusted Agent Protocol, KYA, AP2/ACP); a contract-test strategy (simulator vs real sandbox, live VIC only as a tagged test; Mastercard simulated until the user registers); which payloads we model (token, instruction/mandate, authorisation, settlement); integration points (pre-payment check, authorisation re-check, step-up); what the user must sign up for, and when.
- **Answers the questions the hackathon left open** (they block T-018 and T-026):
  1. In what format does an agent deliver a purchase, and how is it identified (credential mechanism)?
  2. Where does the idempotency key come from in that format?
  3. After APPROVE, what do we return, to whom, and who executes the payment?
  4. How long does a single-use authorisation stay valid (this also sets the ask expiry, T-023/T-025, which must be configurable)?
- **Keeps from ADR-0004:** non-custodial; single-use authorisation bound to the decision; routing inside the decision; "we decide, the issuer executes" wording.

### T-006 Data plan
Brief step 4, from [starting-dataset](data/starting-dataset.md). How each source maps to the domain model and what it's for (replay tests, baselines, fraud signals); a **licence check per dataset** (PaySim, IEEE-CIS, ULB, Sparkov; required by the handoff); what we generate synthetically (injection, split orders, reconciliation mismatches, M17); a broader **versioned, frozen benchmark set** (size open, with T-021/T-020); the loader/adapter design. The FX source is open (T-010).

### T-007 Milestone plan
Brief step 5. Milestones with scope and exit criteria, depth first, starting with a thin slice: one agent, one mandate, one purchase decision (fixture `SCEN0000`). Creates the epics below and re-homes their items. Each epic's acceptance = replays of labelled cases plus invariant property tests. When the user agrees to it, production code may start.

### T-008 Dev toolchain on the Pi
Temurin 25 (SDKMAN, user space); Postgres in Docker Compose with memory limits that fit the NAS; Gradle memory caps (`-Xmx1g`); `.env` (gitignored) for `OPENROUTER_API_KEY`. Pick and verify on the Pi: formatter, static analysis with warnings as errors, coverage and mutation tools for `core`, dependency vulnerability check; propose the coverage and mutation floors for the user to set. First a spike: Boot 4 + Testcontainers + Spring AI → OpenRouter hello on the Pi (confirms ArchUnit/jqwik on JUnit 6, measures context start time). Then wire `./gradlew check` into CI and the Commands section of CLAUDE.md.

## Later: product epics (T-007 orders and splits them)
- **A. Decision core, thin slice:** T-009 project skeleton with quality gates · T-010 Money and FX (configurable base currency) · T-011 append-only audit log + idempotency (add AuditEvent and owner id) · T-012 starter-data adapters with hash check (as test, replay and benchmark adapters) · T-013 API foundation (caller-identity port) · T-014 agent registry with stop switch ("who" from caller identity) · T-015 mandates: model, confirm, diff, revoke · T-016 spend ledger with budget periods · T-017 fixed decision rules and the combiner · T-018 agent purchase endpoint (waits for T-005; depends on T-023).
- **B. Intent check and benchmark (M18, M16):** T-019 shared LLM client with spend cap (OpenRouter only) · T-020 intent check: extractors over a fact catalogue derived from the mandate's item requirements · T-021 benchmark cases (DEMO0001 becomes one regression scenario) · T-022 benchmark against a plain prompt on the versioned set. If the plain prompt comes within ~5 points, the direction is reconsidered.
- **C. Ask the user (M5):** T-023 ask with configurable expiry and re-check, listing every reason.
- **D. Authorise and route (M7, M8):** T-024 payment methods, order and blocking · T-025 single-use authorisation (configurable expiry) · T-026 approved-payment hand-off (waits for T-005) · T-027 Mastercard Agent Pay rail, simulated · T-028 VIC rail: simulator first, live as a tagged contract test.
- **E. Reconcile (M9–M11):** T-029 capture files and mismatch detection, plus new items for the three-way match against settlement and for recurring/refund detection. Amends [ADR-0005](adr/ADR-0005-reconciliation-from-capture-files.md). Open: the real settlement file format (needs an issuer or TSYS sample).
- **F. Conversational ops (M12–M14):** T-030 LLM mandate compiler (words → draft → confirm) · T-031 chat over the log (refunds once reconciliation has them).
- **G. Cardholder app (M15):** from the handoff's `design/` folder.
- **Later, nice-to-have N6:** T-032 x402 crypto rail on testnet, only if the user wants it. Open: how our single-use authorisation reaches the user's wallet; never a private key in the Java app; mainnet refused.

## Gaps nobody owns yet
- Nothing is recorded about what was learned at the hackathon (TSYS, agent formats).
- The handoff's "authorship log" rule (03-constraints, Legal) has no home in this repo yet.

## Settled decisions
- Payment networks are sandboxed or simulated, never live money (2026-10-14). Synthetic and sandbox data only, opaque tokens, never a PAN.
- Shopping agents are external third-party platforms; we build none, only a scripted driver as a test harness.
- The mandate compiler is an LLM that turns words into hard, structured rules, shown back and applied only after confirmation. The LLM is never in the decision path. OpenRouter with a very small budget (~$9.40 total, 2026-10-17).
- No ML risk model early: a `RiskSignal` port with heuristics (invariant 2 keeps it safe to add one later).
- The Leash case is inspiration only: no code, docs or design reused from the `leash` repo.
- Deployment to the Pi is optional and a later epic; local `docker compose` first.
- The handoff's ≈26 Dec 2026 decision gate may cut scope, never the quality bar.
