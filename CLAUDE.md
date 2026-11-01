# Agentic Wallet

Payment wallet for AI shopping agents with a deterministic control & security layer.
Intent, direction and features (V…, M…, N…): [PROJECT_BRIEF.md](PROJECT_BRIEF.md). It replaced the strategist handoff on 2026-10-29. Architecture: [docs/architecture/](docs/architecture/README.md).

## Current phase
**Planning.** Architecture style and module cut proposed in ADR-0006 and ADR-0007, to be confirmed by the walking skeleton (T-037). Stack decided and conventions proposed in ADR-0009; the toolchain is verified and pinned on the Pi ([T-008 report](docs/research/toolchain-on-the-pi.md); the throwaway reactor in `spikes/toolchain/` is the reference T-009 rebuilds at the root). No production code until the plan is agreed: T-037 (skeleton), T-003 (domain model and contracts), T-005 (payment networks), T-006 (data), T-007 (milestones). See [docs/backlog.md](docs/backlog.md). Docs, ADRs and throwaway spikes in `spikes/` (never merged into `src/`) are fine.

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
- The build fails on formatting, static analysis and compiler warnings. Coverage and mutation floors on `core`, set by the user on 2026-11-01 (T-008): line 85 %, branch 90 %, line per class 80 %, mutation score 85 %. Code under `spikes/` is exempt from the floors; everything that reaches `src/` is not.
- No `TODO`/`FIXME` without an issue; no unexplained suppressions; named errors, no swallowed exceptions.
- Every public port in `core` has a contract comment: inputs, failure modes, idempotency.
- Dependencies pinned and checked for known vulnerabilities. Simulated/synthetic data is always labelled. Generated code (e.g. Claude Design output) is a reference, rebuilt to this bar.

## How we work
- One task = one branch = one PR into `main`. `main` is protected: CI must be green, nothing is pushed to it directly. The user approves by merging the PR.
- Any agent that writes, changes, refactors, reviews or deletes code (spikes and tests included) loads the [clean-code](.claude/skills/clean-code/SKILL.md) and [design-patterns](.claude/skills/design-patterns/SKILL.md) skills first, unasked. When you start a subagent for code work, tell it to load them.
- Before opening a PR, run the `reviewer` agent on the branch and fix what it finds.
- SonarQube (user, 2026-11-01): the SonarCloud check on every PR is the gate. Locally, use only the free parts of the SonarQube plugin and `sonar` CLI: before every commit, `sonar analyze secrets <changed files>`; after every push of a PR branch, and before the reviewer runs, read the PR's quality gate and open issues (`sonar qg status -p LukassSoukup_Agentic-Wallet --pull-request <n>` and `sonar list issues -p LukassSoukup_Agentic-Wallet --pull-request <n> --format table`, or the plugin's `sonar-quality-gate` and `sonar-list-issues` skills) and fix what they report. Vortex in-loop analysis and dependency-risk scanning need a paid plan and are not used; no per-edit hook (see the tooling rule below).
- Library docs (user, 2026-11-01): the Context7 MCP server is connected for up-to-date documentation. Use it proactively, unasked, whenever a question touches a stack library's API, configuration or behaviour (Spring Boot, Spring AI, Spring Modulith, Testcontainers, ArchUnit, jetCheck, Error Prone, Vite and the rest of ADR-0009): resolve the library, query at the pinned version, cite the source file it returns. Its code examples are generated and can lag an API move (seen with Testcontainers 2.0's package rename), so verify them against the artifact or the compiler like any generated code.
- Backlog: [docs/backlog.md](docs/backlog.md). Decisions: [docs/adr/](docs/adr/README.md). Language: [glossary](docs/domain/glossary.md).
- Docs link with relative Markdown links (they work on GitHub, in the IDE and in Obsidian). No wikilinks.
- **No workflow tooling** (scripts, hooks, gates) until a real defect has shown up twice.

## Data
- `data/raw/leash-starter/` is an immutable copy; a hook blocks edits and `python3 scripts/data_check.py` verifies its hashes. Derived data goes to `data/derived/` via scripts. Notes: [starting-dataset](docs/data/starting-dataset.md).
- The `leash` repo is off limits. `../leash-analysis` may be read (business and network research; its old handoff is superseded by the brief), except `lukas/` (confidential).
- Never commit secrets, real card numbers or sandbox account ids. Only the names of the settings that hold them.

## Commands
- Data check: `python3 scripts/data_check.py`
- Build and test (the reactor is in `spikes/toolchain/` until T-009 rebuilds it at the root; the JDK is on `PATH` only in a login shell, so pass `JAVA_HOME`): `cd spikes/toolchain && JAVA_HOME=$HOME/.local/opt/jdk-25 ./mvnw verify` runs formatting, Error Prone with warnings as errors, unit, property, architecture, Testcontainers and MCP tests, coverage and mutation on `control-core`, the web build, tests and `npm audit`, and the vulnerability check (`-Pno-vuln-check` skips the NVD download while iterating; the check needs `NVD_API_KEY` exported in the shell, e.g. `set -a; . ./.env; set +a`, since Maven does not read `.env`). Format: `./mvnw spotless:apply`. One module: `./mvnw -pl control-core -am verify -Pno-vuln-check`. Web only: `./mvnw -pl web verify`. Measure the JVMs: `./measure.sh`. CI runs the same `./mvnw verify` ([ci.yml](.github/workflows/ci.yml)).

## Environment
Raspberry Pi 5 (aarch64, 16 GB), also the home server. Docker available; Temurin 25 and Node 26 live under `~/.local/opt` and are on `PATH` only in login shells; no Postgres on the host. Prefer containers for databases; check `free -h` before memory-heavy builds, and expect port 8080 to be taken.
