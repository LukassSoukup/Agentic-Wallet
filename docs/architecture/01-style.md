# 01 Architecture style

Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md). This page keeps the reasoning.

## The forces
| Force | Where it comes from | What it pushes toward |
|---|---|---|
| The agent harness talks to an LLM and reads untrusted shop text; the control layer must stay small, auditable and unreachable by a prompt injection | invariants 2 and 3; the product's position as an agent platform | a process boundary between the harness and the control layer |
| A decision, its ledger reservation, its audit record, its idempotency key and the pre-approval the bank's hook will match must commit together | invariants 1, 5, 7, 8 | one ACID transaction, so one process and one database around the decision |
| The bank processor's authorisation hook gives 800 ms to 2 s in total; our share is about 300 ms at p99, and it must answer while the harness or the LLM is down | processor documentation (Thredd, Enfuce, Stripe, Adyen) | a small, separately deployed, read-mostly hook responder |
| The same artefacts run in our EU cloud or inside a bank's perimeter, for one bank per instance | product decision | few moving parts; every extra service is extra work for the bank's operations and its outsourcing register |
| One developer with AI agents until a pilot signal; development on a Raspberry Pi 5 with 16 GB | business plan; [CLAUDE.md](../../CLAUDE.md) | no distributed-systems cost that nothing pays for |
| Payment network interfaces change at their own pace and hold signing keys and credentials | network research | network adapters behind ports, only in the trusted zone |
| Quality first: every rule tested, contract suites per port, property tests for money | [CLAUDE.md](../../CLAUDE.md) | boundaries checkable by the build, a pure core testable without any framework |

## The nine styles, scored
| Style | Verdict | Why |
|---|---|---|
| Monolith (one deployable) | rejected | The harness (untrusted, talks to LLMs and the web) and the control layer (trusted) must not share a process. |
| Layered, as the top-level style | rejected as the top level | Layers by technical concern spread every business rule across presentation, service and data layers. Inside each module we use ports and adapters instead, which keeps the domain free of the framework. |
| **Modular monolith** | **chosen for the control backend** | Mandates, decisions, ledger, audit, payments and reconciliation share invariants that need one transaction. Modules with checked boundaries give the separation without the network. A module can still be extracted later, because it already talks through a published API and events. |
| Microkernel (plug-ins) | **used inside modules** | The decision rules and the per-network checkout adapters are a core plus plug-ins (Strategy behind a port). It is a pattern inside a module, not the shape of the system. |
| Microservices | rejected for now | Splitting decisioning, ledger and audit into services turns invariants 5 and 7 into sagas and eventual consistency: the wrong failure mode for money. About eight JVMs on the Pi and as many services for a bank to operate, for one developer. Revisit only if a module's load or change rate demands it. |
| **Service-based** | **chosen for the system** | A few coarse deployables, split where trust or latency demands it, sharing one PostgreSQL with a schema and a role each. The hook responder reads the backend's pre-approvals straight from the database, so a fresh approval is visible at once without a broker. |
| Service-oriented (ESB) | rejected | An enterprise bus solves integration across many owners; we have one owner and a handful of backend deployables. |
| **Event-driven** | **used at the seams** | Follow-ups after a decision (checkout, read models, notifications, network instruction updates, reconciliation) run as events from a transactional outbox. The decision itself stays synchronous: the user waits in chat, and invariant 1 is a gate, not a notification. |
| Space-based | rejected | Volumes are small. The only echo is that the hook responder answers from a read model built for that one query. |

## Result
**A service-based system whose trusted backend is a hexagonal modular monolith, with event-driven seams through a transactional outbox.** The deployables and how they talk are in [02-system.md](02-system.md); the modules in [03-modules.md](03-modules.md).

## What an outbox is, and why it matters without a broker
After a decision commits, other work must follow: complete the checkout, update the activity page, tell the user about a step-up, refresh what the hook responder reads. Writing to the database and then sending a message is two writes; a crash between them loses the follow-up, and sending first announces something that may roll back. An **outbox** stores the event as a row in the same transaction as the business change. A relay delivers unsent rows to their listeners and marks them done, retrying after a crash, so each event arrives at least once and in no guaranteed order; listeners are idempotent (invariant 7) and tolerate reordering ([06-consistency.md](06-consistency.md) §7). Spring Modulith's event publication registry provides this on PostgreSQL. "Messaging" here means reliable events between modules; a broker is only one way to carry them between processes. When a second process needs the events, the same outbox feeds RabbitMQ and nothing else changes.

The audit record is **not** an outbox event: it is written in the decision's own transaction, so a decision without its audit row cannot exist.

## When to revisit
- A module needs to scale or deploy on its own (measured, not expected).
- A bank requires a component in a different network zone than the rest of the backend.
- A second process needs the backend's events (then add RabbitMQ behind the outbox).
