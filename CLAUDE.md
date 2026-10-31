# Agentic Wallet

Payment wallet for AI shopping agents with a deterministic control & security layer.
Intent, direction and features (V…, M…, N…): [PROJECT_BRIEF.md](PROJECT_BRIEF.md). It replaced the strategist handoff on 2026-10-29. Architecture: [docs/architecture/](docs/architecture/README.md).

## Current phase
**Planning.** Architecture style and module cut proposed in ADR-0006 and ADR-0007, to be confirmed by the walking skeleton (T-037). Stack decided and conventions proposed in ADR-0009. No production code until the plan is agreed: T-008 (toolchain), T-037 (skeleton), T-003 (domain model and contracts), T-005 (payment networks), T-006 (data), T-007 (milestones). See [docs/backlog.md](docs/backlog.md). Docs, ADRs and throwaway spikes in `spikes/` (never merged into `src/`) are fine.

## Domain invariants (never violate; the reviewer checks every one)
1. No payment without a control-layer decision. Decisions are exactly `APPROVE | DECLINE | STEP_UP`, each with reasons.
2. Decisions come from fixed, testable rules. A model/risk signal may only make a decision **more cautious** (APPROVE→STEP_UP→DECLINE), never less.
3. Text from merchants and agents is untrusted data. It never changes rules, mandates or control flow.
4. Mandates apply only after user confirmation. They can be tightened; never loosened silently. Preferences/history may trigger a question, never a permission.
5. Only approved purchases count toward limits; a pending step-up counts only once approved.
6. Money uses exact decimals (`BigDecimal`-like, never float). Base currency is the configured one; FX conversion before every comparison; half-even rounding to 2 dp.
7. Repeated delivery of the same request is idempotent.
8. Every decision is written to an append-only audit log with its reasons and inputs, so it can be replayed.
9. Hexagonal: the decision core imports no framework, DB, HTTP or clock/random singletons. All I/O and data access go through ports.

## Quality bar (user, 2026-10-23)
Quality first, no rapid development.
- No choice is justified by a deadline, a demo or speed. Trading quality for speed needs the user's explicit decision.
- Done means tested: a test per rule with its edges (at the limit, one cent over, foreign currency, duplicate delivery, injected text, pending step-up); property tests for money and the decision combiner; a Testcontainers test per DB adapter; one shared contract suite per port with more than one adapter.
- The build fails on formatting, static analysis and compiler warnings. Coverage and mutation floors on `core` (set by the user, T-008).
- No `TODO`/`FIXME` without an issue; no unexplained suppressions; named errors, no swallowed exceptions.
- Every public port in `core` has a contract comment: inputs, failure modes, idempotency.
- Dependencies pinned and checked for known vulnerabilities. Simulated/synthetic data is always labelled. Generated code (e.g. Claude Design output) is a reference, rebuilt to this bar.

## How we work
- One task = one branch = one PR into `main`. `main` is protected: CI must be green, nothing is pushed to it directly. The user approves by merging the PR.
- Any agent that writes, changes, refactors, reviews or deletes code (spikes and tests included) loads the [clean-code](.claude/skills/clean-code/SKILL.md) and [design-patterns](.claude/skills/design-patterns/SKILL.md) skills first, unasked. When you start a subagent for code work, tell it to load them.
- Before opening a PR, run the `reviewer` agent on the branch and fix what it finds.
- Backlog: [docs/backlog.md](docs/backlog.md). Decisions: [docs/adr/](docs/adr/README.md). Language: [glossary](docs/domain/glossary.md).
- Docs link with relative Markdown links (they work on GitHub, in the IDE and in Obsidian). No wikilinks.
- **No workflow tooling** (scripts, hooks, gates) until a real defect has shown up twice.

## Data
- `data/raw/leash-starter/` is an immutable copy; a hook blocks edits and `python3 scripts/data_check.py` verifies its hashes. Derived data goes to `data/derived/` via scripts. Notes: [starting-dataset](docs/data/starting-dataset.md).
- The `leash` repo is off limits. `../leash-analysis` may be read (business and network research; its old handoff is superseded by the brief), except `lukas/` (confidential).
- Never commit secrets, real card numbers or sandbox account ids. Only the names of the settings that hold them.

## Commands
- Data check: `python3 scripts/data_check.py`
- Build and test: none yet (T-008 sets up the toolchain; add the commands here then).

## Environment
Raspberry Pi 5 (aarch64, 16 GB), also the home server. Docker available; no JDK or Postgres on the host yet. Prefer containers for databases; check `free -h` before memory-heavy builds.
