# Architecture Decision Records

One file per product decision: `ADR-NNNN-<slug>.md`, from `_template.md`. Status: `proposed` → `accepted` (after the user agrees) → possibly `superseded by ADR-…`. Never edit the decision text of an accepted ADR; supersede it with a new one. Numbering continues from the old `wallet_control` repo (ADR-0001…0004 and 0010…0011 were workflow ADRs and stayed there). `TASK-`/`EPIC-` ids in these files are the old repo's cards; the open ones are in [backlog.md](../backlog.md).

| ADR | Title | Status |
|---|---|---|
| [ADR-0005-tech-stack](ADR-0005-tech-stack.md) | Tech stack: Java 25, Spring Boot 4, PostgreSQL with plain SQL, React + TypeScript UI | accepted (re-opened by TASK-048; to be superseded via TASK-050) |
| [ADR-0006-domain-model-and-ports](ADR-0006-domain-model-and-ports.md) | Domain model and ports for the demo epic | proposed (to be superseded via TASK-003) |
| [ADR-0007-model-use-caution-only](ADR-0007-model-use-caution-only.md) | Model use: extract and flag, caution only, fail closed | proposed (amended by TASK-048) |
| [ADR-0008-payment-methods-authorisation-rails](ADR-0008-payment-methods-authorisation-rails.md) | Payment methods, single-use authorisation and rails | proposed (to be superseded via TASK-007) |
| [ADR-0009-reconciliation-from-capture-files](ADR-0009-reconciliation-from-capture-files.md) | Reconciliation from issuer-style capture files | proposed (to be amended in the reconcile epic) |
