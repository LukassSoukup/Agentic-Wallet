---
id: ADR-0006
status: proposed
date: 2026-10-18
tasks: ["EPIC-021"]
---
# ADR-0006: Domain model and ports for the demo epic

> **Re-opened 2026-10-23 by TASK-048:** narrowed to the demo epic. To be superseded by the ADR of TASK-003 (owner and caller identity, Settlement and Refund, `RiskSignal`, `ScenarioSource` placement).

## Context
TASK-003 is narrowed to what EPIC-021 needs (user, 2026-10-18). Stack and modules per [ADR-0005-tech-stack](ADR-0005-tech-stack.md): `core` (pure), `app` (Spring adapters, REST + SSE), `web` (React + TypeScript). Invariant 9.

## Options considered
1. **One pure decision core with ports (chosen).** Every input (mandate, ledger, history, agents, methods, FX, clock, LLM) arrives through a port; the core returns a decision value; adapters persist and execute.
2. Rules spread over Spring services. Faster to start, but the core can't be tested or replayed without the framework.

## Decision
- **Aggregates in `core`:** Agent; Mandate (versions, confirm, revoke; global budget and per-agent limits with periods); PaymentMethod; PurchaseProposal; Decision (outcome, reasons, rule results, flags); Ask; PaymentAuthorisation; Capture; MismatchCase.
- **Value types:** `Money`, `UntrustedText`, `IdempotencyKey`, `Period`.
- **Ports:** `Clock`, `FxRates`, `MandateStore`, `MandateCompiler`, `AgentDirectory`, `PaymentMethods`, `MerchantDirectory`, `PurchaseHistory`, `ScenarioSource`, `SpendLedger`, `AuditLog`, `IntentModel`, `PaymentRail`, `CaptureSource`, `ChatModel`.
- **Decision flow:** idempotency lookup → hard rules → intent check → routing feasibility → caution combiner (outcome only rises: APPROVE < STEP_UP < DECLINE) → audit record → then, only for APPROVE, an authorisation.
- **Hard limits decline:** over a limit or budget is DECLINE; ambiguity (duplicates, split orders, unfamiliar shop, intent deviation, hostile text) is STEP_UP; missing data takes the mandate's "when unsure".
- **Callers:** no user auth (single user), but two API areas: `/api/cardholder/**` for the user, needing the cardholder key from `.env`, and `/api/agent/**` for agents. Agents only deliver proposed purchases with their order data (user, 2026-10-18); how they authenticate and in what format they deliver is a Day-1 question. An agent's identity always comes from its credential, never from the request body. Mandates, asks, payment methods, blocking and the agent registry's status changes (suspend, resume, revoke, stop all) are cardholder-only; an agent can never change its own status.
- **Idempotency:** a repeated key returns the stored original decision; a key with a different payload is a conflict.

## Consequences
- \+ Every decision replays from its record without any adapter with side effects.
- \+ Each subtask owns one module, so agents can work in parallel.
- − More types up front; the skeleton subtask sets the pattern.
