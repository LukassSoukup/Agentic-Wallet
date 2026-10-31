---
id: ADR-0003
status: proposed
date: 2026-10-18
tasks: []
---
# ADR-0003: Model use: extract and flag, caution only, fail closed

> **Re-opened 2026-10-23:** amended in place: one provider instead of a second one for the event; the demo framing removed; the extractor set no longer bounded by the demo fixtures.

> **Amended by [ADR-0007](ADR-0007-cart-evidence-and-auto-purchase.md) (2026-10-29):** the model runs in the control backend outside the pure core, answers typed questions about merchant-sourced text, and its provider is chosen per T-036.

## Context
The handoff makes the intent check (M18) the core and warns that a plain prompt might match it ("a GPT wrapper"). Invariants 2 and 3. Decided with the user on 2026-10-18. The glossary's earlier rule that the mandate compiler never takes part in a decision still holds.

## Options considered
1. **Hybrid (chosen):** regex and fixed checks first; the LLM fills only facts they can't settle, in a fixed schema; fixed rules compare.
2. Deterministic only: safest, weaker on free text.
3. The LLM judges directly: the wrapper the benchmark must beat.

## Decision
- **Intent check:** the LLM extracts facts only, from shop text quoted as data, validated against a schema; fixed rules turn facts into a verdict. A fact settled only by the model can lead to "deviates" or "uncertain", never to "matches": the model can find a problem, never clear one (user, 2026-10-18). So for every case the outcome with the model is at least as cautious as with the model failed. Hostile text raises the outcome to at least STEP_UP.
- **Failure:** timeout, invalid output, provider error or spend cap → "uncertain" → the mandate's "when unsure" (ask or decline), never approve.
- **Replay:** model inputs and outputs are stored in the decision record; replays never call a model.
- **Chat:** the model chooses among read-only tools; amounts and ids shown come from tool results; no write tools; rule changes become drafts for the confirm step.
- **Mandate compiler:** words to a draft only; nothing applies before the user confirms.
- **Providers:** one remote adapter (OpenRouter, the model id configurable) and a fixture adapter for tests and offline runs; a spend cap in the adapter (user, 2026-10-23).

## Consequences
- \+ A model outage only affects ambiguous cases; the product keeps deciding.
- \+ The benchmark compares a real engine against a plain prompt.
- − Extractors take work per fact type; they cover a fact catalogue derived from the mandate's item requirements, checked against a versioned benchmark set.
