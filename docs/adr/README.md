# Architecture Decision Records

One file per product decision: `ADR-NNNN-<slug>.md`, from `_template.md`. Status: `proposed` → `accepted` (after the user agrees) → possibly `superseded by ADR-…`. Never edit the decision text of an accepted ADR; supersede it with a new one. Numbered in order from ADR-0001, never reused; **next free number: ADR-0006**. Renumbered once in T-002 (ids and references only, no decision text changed). `tasks:` lists the `T-NNN` items that produced the ADR; open ones are in [backlog.md](../backlog.md), finished ones in git history and PR titles.

| ADR | Title | Status |
|---|---|---|
| [ADR-0001-tech-stack](ADR-0001-tech-stack.md) | Tech stack: Java 25, Spring Boot 4, PostgreSQL with plain SQL, React + TypeScript UI | accepted (re-opened 2026-10-23; to be superseded via T-004) |
| [ADR-0002-domain-model-and-ports](ADR-0002-domain-model-and-ports.md) | Domain model and ports for the demo epic | proposed (to be superseded via T-003) |
| [ADR-0003-model-use-caution-only](ADR-0003-model-use-caution-only.md) | Model use: extract and flag, caution only, fail closed | proposed (amended 2026-10-23) |
| [ADR-0004-payment-methods-authorisation-rails](ADR-0004-payment-methods-authorisation-rails.md) | Payment methods, single-use authorisation and rails | proposed (to be superseded via T-005) |
| [ADR-0005-reconciliation-from-capture-files](ADR-0005-reconciliation-from-capture-files.md) | Reconciliation from issuer-style capture files | proposed (to be amended in the reconcile epic) |
