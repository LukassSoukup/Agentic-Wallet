# Starting dataset

Location: `data/raw/leash-starter/`, copied 2026-10-14 from `~/IdeaProjects/leash/data/` (all 18 SHA-256 hashes in `metadata.json` verified). Immutable (`scripts/data_check.py` verifies the hashes in CI). The base currency is configurable, so the pack stays in CHF; the earlier plan to derive a CZK copy was dropped.

First notes from reading `README.md` and `data_dictionary.md` on 2026-10-14. The full data plan is T-006 in [backlog.md](../backlog.md).

## Contents
Synthetic, with no answer key. 20 customers → 31 accounts → 41 cards; 58 merchants; 66 items; 4 FX rates; 4,701 historical authorizations (2025-09-01 to 2026-07-31, 9.6% agent-initiated); 5 scenarios with 45 purchase attempts and 56 cart lines. JSON schemas in `schemas/`. `metadata.json` holds SHA-256 hashes, so the pack can be verified on load.

## Findings that matter for design
- **The base currency is CHF, not CZK.** All `_chf` fields, FX rates (CHF/EUR/GBP/USD → CHF) and scenario instructions ("CHF 120 per order") use CHF, while the brief says CZK. → settled: the base currency is configurable (line 3, invariant 6); built in T-010.
- Each scenario is one plain-language cardholder instruction plus an ordered list of attempts. It maps directly to *mandate → decisions*, and is good for replay tests.
- Period spend (`spend_in_period_before_chf`) is deliberately empty. The control layer has to track approved spend itself, and pending step-ups don't count.
- Velocity is defined: earlier attempts in the window `[t-10min, t)`, whatever their status.
- `item_details` may contain injected instructions, so it's a test source for untrusted-text handling.
- Traps: join on IDs, never names (near-duplicate merchant names). Currency is never derived from merchant country. `mobile_wallet` can be card-present or not. `TR…` (history) and `AU…` (attempts) are separate ID namespaces.
- The history's lifetime counters are scoped to the card. They are not period totals.
