---
id: ADR-0005
status: proposed
date: 2026-10-18
tasks: []
---
# ADR-0005: Reconciliation from issuer-style capture files

> **Re-opened 2026-10-23:** scope cut to captures for the demo. To be amended in the reconcile epic: settlement and the three-way match (M9), recurring and refund detection (M10); the real file format stays an open question until an issuer or TSYS sample exists.

## Context
Handoff M9 to M11; the regulation note allows "reconciliation from files the issuer or processor supplies". The TSYS sandbox is unknown until Day 1. Decided with the user on 2026-10-18.

## Options considered
1. **Files through a `CaptureSource` port, from a synthetic generator (chosen).**
2. In-process fake events: simpler, further from the legal framing.
3. Wait for TSYS: unknown.

## Decision
- CSV and JSON capture files are read from a folder; import is idempotent.
- Captures match decisions by authorisation reference, converted to CZK first.
- Mismatch types now: amount differs, captured twice, charge with no decision. Settlement, refunds and one-off-to-recurring come later.
- A mismatch opens a case with a drafted evidence package; nothing is filed automatically.

## Consequences
- \+ TSYS becomes one more `CaptureSource` adapter.
- − The demo's capture is synthetic and must say so.
