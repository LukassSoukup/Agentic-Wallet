# Architecture

The backend's architecture style, its deployables and its module cut, agreed with the user in the brainstorm of 2026-10-29, amended on 2026-10-30 after an end-to-end walkthrough of an unattended price watch and on 2026-10-31 after one of a grocery order at a merchant with its own MCP server. The decisions are recorded in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md) (style, deployables, module cut), [ADR-0007](../adr/ADR-0007-cart-evidence-and-auto-purchase.md) (where the cart comes from and when a purchase may go ahead without asking) and [ADR-0008](../adr/ADR-0008-merchant-connectors-and-the-connector-catalogue.md) (merchant connectors, the connector catalogue and the connector gateway). These pages explain them; where they differ, the ADRs win.

| Page | What it answers |
|---|---|
| [01-style.md](01-style.md) | Which architecture style, and why the others were rejected |
| [02-system.md](02-system.md) | The deployables, trust zones, how they talk, how they are deployed |
| [03-modules.md](03-modules.md) | The module cut inside each deployable, the Maven layout, the events |
| [04-purchase-flow.md](04-purchase-flow.md) | One purchase end to end: proposal, cart evidence, decision, step-up, checkout, hook, reconciliation; the same flow for a standing mandate without the user and for an order through a merchant connector |
| [05-open-items.md](05-open-items.md) | What this design leaves open, and who or what closes it |
| [06-consistency.md](06-consistency.md) | Budget locking, step-up re-check, checkout attempts, pre-approval consumption, idempotency, the replay unit, event delivery |
| [07-walking-skeleton.md](07-walking-skeleton.md) | What the walking skeleton (T-037) confirmed on the Pi: the mechanics under race, duplicate, timeout, redelivery and a killed process; memory, start times and hook latency; the findings that sharpen the design |
| [diagrams/](diagrams/) | The same architecture as DuctTape.io diagrams, for reference: a one-page overview, the control backend as components with their data in and out, the system with its trust zones (02), the backend modules and events (03), one purchase end to end (04), each as a DuctTape import file (`.json`, "Import from JSON") and a Mermaid export (`.mmd`, "From Mermaid"); the pages and ADRs win where they differ |

## In one paragraph
A **service-based** system of five backend deployables plus the web app: an untrusted **agent harness** (chat, the user's chosen LLM, shopping tools, memory); a thin **control MCP server** that is the only door from the untrusted side into the control layer; a thin **connector gateway** through which the agent may read the catalogues of admitted merchants and nothing else; a trusted **control backend** built as a **hexagonal modular monolith** (mandates, decisions, ledger, payments and checkout, audit, reconciliation); and a small **hook responder** that answers the bank processor's real-time authorisation call. One PostgreSQL instance serves the control backend and the hook responder, with a schema per module and a role per deployable; the harness has its own database. Inside the backend, modules talk through in-process calls and through events made reliable by a **transactional outbox**; no message broker in v1. The decision core is a pure Java module with no dependencies, so invariant 9 is enforced by the compiler.

## Not covered here
The stack and the cross-cutting conventions ([ADR-0009](../adr/ADR-0009-tech-stack-revision.md); versions pinned in T-008, see [toolchain-on-the-pi](../research/toolchain-on-the-pi.md)), the aggregates and port contracts in full (T-003), the payment network adapters and simulators (T-005), the data plan (T-006) and the milestones (T-007). See [backlog.md](../backlog.md).
