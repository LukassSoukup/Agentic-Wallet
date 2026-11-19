# 01 Architecture style

Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md); the forces re-scored on 2026-11-19 for the standalone app of [ADR-0010](../adr/ADR-0010-standalone-app-and-provider-payment-tokens.md), which removed the bank's hook and left the style standing. This page keeps the reasoning.

## The forces
| Force | Where it comes from | What it pushes toward |
|---|---|---|
| The agent harness talks to an LLM and reads untrusted shop text and reviews; the control layer must stay small, auditable and unreachable by a prompt injection | invariants 2 and 3; the product's position as an agent platform | a process boundary between the harness and the control layer |
| A decision, its ledger reservation, its audit record, its idempotency key and the token terms the payment will be bound to must commit together | invariants 1, 5, 7, 8 | one ACID transaction, so one process and one database around the decision |
| The payment provider calls us back: token state (`requires_action`, `used`, `deactivated`) arrives as signed webhooks from the public internet, at least once and in any order | [ADR-0010](../adr/ADR-0010-standalone-app-and-provider-payment-tokens.md); the provider's documentation | one public, signature-verified ingress on the trusted backend, deduplicated and insert-only; no new deployable for it |
| One multi-tenant installation in an EU cloud for consumers anywhere in the EU; load grows with users, not with banks | product decision (2026-11-19) | every lookup scoped to the caller's cardholder; a backend that can later run as more than one process without a redesign (an open item, measured before it is built) |
| One developer with AI agents until a pilot signal; development on a Raspberry Pi 5 with 16 GB | business plan; [CLAUDE.md](../../CLAUDE.md) | no distributed-systems cost that nothing pays for |
| The payment provider's interface is a preview product that changes at its own pace and needs a secret key and a webhook signing secret | provider research ([payments-for-a-standalone-app](../research/payments-for-a-standalone-app.md)) | the provider behind a port, only in the trusted zone, with a key restricted to tokens |
| Quality first: every rule tested, contract suites per port, property tests for money | [CLAUDE.md](../../CLAUDE.md) | boundaries checkable by the build, a pure core testable without any framework |

The force that once justified a fifth deployable, the bank processor's authorisation hook with about 300 ms at p99, is gone with the bank: nothing in v1 has to answer a third party in real time.

## The nine styles, scored
| Style | Verdict | Why |
|---|---|---|
| Monolith (one deployable) | rejected | The harness (untrusted, talks to LLMs and the web) and the control layer (trusted) must not share a process: invariant 2 (no model approves) and invariant 3 (untrusted text never changes control flow) become a network fact only across a process boundary. |
| Layered, as the top-level style | rejected as the top level | Layers by technical concern spread every business rule across presentation, service and data layers. Inside each module we use ports and adapters instead, which keeps the domain free of the framework. |
| **Modular monolith** | **chosen for the control backend** | Mandates, decisions, ledger, audit, payments and reconciliation share invariants that need one transaction. Modules with checked boundaries give the separation without the network. A module can still be extracted later, because it already talks through a published API and events. |
| Microkernel (plug-ins) | **used inside modules** | The decision rules, the per-protocol checkout adapters (UCP, ACP) and the per-provider payment adapters (Stripe SPT now; network agent tokens through a partner and Google Pay later) are a core plus plug-ins (Strategy behind a port). It is a pattern inside a module, not the shape of the system. |
| Microservices | rejected for now | Splitting decisioning, ledger and audit into services turns invariants 5 and 7 into sagas and eventual consistency: the wrong failure mode for money. About eight JVMs on the Pi and as many services to operate, for one developer. Revisit only if a module's load or change rate demands it. |
| **Service-based** | **chosen for the system** | A few coarse deployables, split where trust demands it (harness, MCP door, connector gateway, backend), sharing one PostgreSQL with a schema and a role each. The harness and `control-mcp` split rests on invariants 2 and 3, not on latency, so it survives the loss of the hook. |
| Service-oriented (ESB) | rejected | An enterprise bus solves integration across many owners; we have one owner and a handful of backend deployables. |
| **Event-driven** | **used at the seams** | Follow-ups after a decision (checkout and the token, read models, notifications, token revocation after a mandate revocation or a stop, reconciliation) run as events from a transactional outbox. The decision itself stays synchronous: the user waits in chat, and invariant 1 is a gate, not a notification. |
| Space-based | rejected | Volumes are small, and nothing answers a third party within a hard latency budget. |

## Result
**A service-based system whose trusted backend is a hexagonal modular monolith, with event-driven seams through a transactional outbox.** The deployables and how they talk are in [02-system.md](02-system.md); the modules in [03-modules.md](03-modules.md).

## What an outbox is, and why it matters without a broker
After a decision commits, other work must follow: issue the token and complete the checkout, update the activity page, tell the user about a step-up. Writing to the database and then sending a message is two writes; a crash between them loses the follow-up, and sending first announces something that may roll back. An **outbox** stores the event as a row in the same transaction as the business change. A relay delivers unsent rows to their listeners and marks them done, retrying after a crash, so each event arrives at least once and in no guaranteed order; listeners are idempotent (invariant 7) and tolerate reordering ([06-consistency.md](06-consistency.md) §7). Spring Modulith's event publication registry provides this on PostgreSQL. "Messaging" here means reliable events between modules; a broker is only one way to carry them between processes. When a second process needs the events, the same outbox feeds RabbitMQ and nothing else changes.

The audit record is **not** an outbox event: it is written in the decision's own transaction, so a decision without its audit row cannot exist.

## When to revisit
- A module needs to scale or deploy on its own (measured, not expected), for example the webhook ingress under consumer load.
- More than one `control-backend` process is needed for load or availability ([05-open-items.md](05-open-items.md)).
- A second process needs the backend's events (then add RabbitMQ behind the outbox).
- Banks later: a bank channel that wants its own authorisation check or a component inside its perimeter is a new decision, not configuration.
