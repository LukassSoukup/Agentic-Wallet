# Architecture Decision Records

One file per product decision: `ADR-NNNN-<slug>.md`, from `_template.md`. Status: `proposed` → `accepted` (after the user agrees) → possibly `superseded by ADR-…`. Never edit the decision text of an accepted ADR; supersede it with a new one. Numbered in order from ADR-0001, never reused; **next free number: ADR-0008**. Renumbered once in T-002 (ids and references only, no decision text changed). `tasks:` lists the `T-NNN` items that produced the ADR; open ones are in [backlog.md](../backlog.md), finished ones in git history and PR titles.

| ADR | Title | Status |
|---|---|---|
| [ADR-0001-tech-stack](ADR-0001-tech-stack.md) | Tech stack: Java 25, Spring Boot 4, PostgreSQL with plain SQL, React + TypeScript UI | accepted (re-opened 2026-10-23; module layout superseded by ADR-0006; the rest to be superseded via T-004) |
| [ADR-0002-domain-model-and-ports](ADR-0002-domain-model-and-ports.md) | Domain model and ports for the demo epic | proposed (to be superseded via T-003) |
| [ADR-0003-model-use-caution-only](ADR-0003-model-use-caution-only.md) | Model use: extract and flag, caution only, fail closed | proposed (amended 2026-10-23; amended by ADR-0007) |
| [ADR-0004-payment-methods-authorisation-rails](ADR-0004-payment-methods-authorisation-rails.md) | Payment methods, single-use authorisation and rails | proposed (to be superseded via T-005) |
| [ADR-0005-reconciliation-from-capture-files](ADR-0005-reconciliation-from-capture-files.md) | Reconciliation from issuer-style capture files | proposed (to be amended in the reconcile epic) |
| [ADR-0006-architecture-style-and-module-cut](ADR-0006-architecture-style-and-module-cut.md) | Architecture style, deployables and module cut | proposed (accepted after the walking skeleton T-037 confirms it) |
| [ADR-0007-cart-evidence-and-auto-purchase](ADR-0007-cart-evidence-and-auto-purchase.md) | Cart evidence and when a purchase may go ahead without asking | proposed (accepted after T-034 confirms what a checkout session returns) |
