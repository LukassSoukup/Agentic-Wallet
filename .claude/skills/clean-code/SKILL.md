---
name: clean-code
description: Clean-code and refactoring practice for this repo, from refactoring.guru (code smells, refactoring techniques) plus Fowler, Martin, Beck, Bloch and the project's own invariants. Use PROACTIVELY, without being asked, whenever you write, change, refactor, review, move or delete code here (Java, TypeScript, SQL, Python scripts, tests, spikes), before writing the first line and again before calling the change done.
---

# Clean code

Clean code is code another developer can read, change and trust without asking you. This skill says how to write it here and how to keep it that way. Pair it with the [design-patterns](../design-patterns/SKILL.md) skill when a change adds or reshapes types.

Sources: [refactoring.guru: clean code, smells, techniques](https://refactoring.guru/refactoring); Fowler, *Refactoring* (2nd ed.); Martin, *Clean Code*; Beck's four rules of simple design; Bloch, *Effective Java* (3rd ed.); Evans, *Domain-Driven Design*; Cockburn, hexagonal architecture. The project's [CLAUDE.md](../../../CLAUDE.md) wins over this skill wherever they differ.

## When to use it
Every time an agent writes, updates, maintains or deletes code: production code, tests, migrations, scripts and throwaway spikes alike. Load it before you start, not after the review complains.

## Workflow
1. **Before writing.** Read the code you will touch and its tests. Name the smells already there ([code-smells.md](references/code-smells.md)). Decide whether the change needs a preparatory refactoring first ("make the change easy, then make the easy change", Beck).
2. **Test first.** Write the failing test for the behaviour, with the edges the quality bar lists (at the limit, one cent over, foreign currency, duplicate delivery, injected text, pending step-up).
3. **Write the simplest code that passes.** No speculative hooks, flags or parameters.
4. **Refactor while green.** Small, named steps from [refactorings.md](references/refactorings.md), tests run after each one.
5. **Before calling it done,** walk the checklist at the end of this file.

Keep refactoring and behaviour change apart: separate commits at least, a separate PR when the refactoring is large. A refactoring commit changes no test expectation.

## Simple design (Beck), in priority order
1. Passes all the tests.
2. Reveals intent.
3. No duplication of knowledge (DRY is about knowledge, not similar-looking lines).
4. Fewest elements: no class, method or abstraction that the first three rules don't need.

## Rules

### Names
- Use the words of the [glossary](../../../docs/domain/glossary.md) and the ADR names for types and ports (`Mandate`, `Decision`, step-up, `UntrustedText`, `SpendLedger` from [ADR-0006](../../../docs/adr/ADR-0006-domain-model-and-ports.md)). No synonyms (`Rule` vs `Policy` vs `Constraint` for one idea), no new terms without adding them to the glossary.
- Classes are nouns, methods are verbs, booleans read as predicates (`isWithinLimit`, `hasPendingStepUp`).
- Name for the reader, not the implementation: `remainingBudget`, not `tmpVal2`, `map`, `data`, `helper`, `manager`, `util`.
- Units and currency belong in the type, not the name (`Money`, `Duration`), never `amountCzk: BigDecimal`.

### Functions
- One level of abstraction per function; one reason to exist. If you need "and" to describe it, split it.
- Short enough to read without scrolling. Length is a hint, intent is the rule.
- Few parameters; three or more that travel together are a missing type (Introduce Parameter Object).
- No flag arguments (`decide(p, true)`): two methods or a named enum.
- Command–query separation: a method either returns a value or changes state, not both. Idempotent operations are the documented exception.
- Guard clauses over nested `if`s; no `else` after `return`.
- No hidden side effects, no output parameters, no `null` returns from public methods (use `Optional` or a Null Object, see design-patterns).

### Types and data
- **Make illegal states unrepresentable.** Validate in constructors and factories; once built, a value is valid. Parse, don't validate: untrusted input becomes a typed value at the edge or is rejected there.
- Value objects for domain primitives: `Money`, `Currency`, `IdempotencyKey`, `AgentId`, never raw `String`/`BigDecimal`/`long` crossing a method boundary (Primitive Obsession).
- Immutable by default: Java `record`s, `final` fields, `List.copyOf`; TypeScript `readonly`. Mutability needs a reason.
- Model closed sets as enums or sealed types (`Outcome = APPROVE | DECLINE | STEP_UP`), and switch over them exhaustively so the compiler finds the missing case.
- Encapsulate collections; never hand out a mutable internal list.

### Classes and modules
- SOLID, applied with judgment: single responsibility, open for extension where variation is real, honest subtypes (Liskov), small role interfaces, depend on abstractions at module boundaries.
- Tell, don't ask: move behaviour to the data it uses (Feature Envy).
- Law of Demeter: talk to direct collaborators, not `a.getB().getC().doIt()`.
- Composition over inheritance. Inheritance only for a real is-a with shared invariants.
- Hexagonal (invariant 9): the core imports no framework, DB, HTTP, clock or random. Time, randomness, FX, storage and models come in through ports. Adapters translate; they hold no business rules.

### Errors
- Named exception types per failure (`MandateNotConfirmedException`, `UnsupportedCurrencyException`), never bare `RuntimeException`/`Exception`.
- Expected business outcomes (DECLINE, STEP_UP, an idempotency conflict) are values, not exceptions.
- Never swallow: no empty `catch`, no log-and-continue that hides a failure. Catch only what you can handle; wrap with context and the cause when crossing a layer.
- Fail fast on broken preconditions at the boundary; trust validated types inside.
- No PII, card data, secrets or untrusted text in exception messages or logs without deliberate redaction.

### Comments and documentation
- Code says *what* and *how*; comments say *why*, or state a contract. A comment explaining a confusing block is a request for Extract Method or a better name.
- Every public port in `core` has a contract comment: inputs, failure modes, idempotency (quality bar).
- No commented-out code, no journal comments, no `TODO`/`FIXME` without an issue id, no unexplained suppression (`@SuppressWarnings` and lint disables carry a reason).

### Money and the domain (the reviewer checks these)
- `BigDecimal`/`Money` only, never `double`/`float`/JS `number` for amounts. Half-even to 2 dp, explicit `RoundingMode` at every scale change; compare with `compareTo`, not `equals`.
- FX conversion to the base currency before every comparison, through the `FxRates` port.
- Untrusted text stays wrapped in `UntrustedText`; it never reaches control flow, a rule or a mandate.
- A risk or model signal only moves an outcome towards caution.
- No `Instant.now()`, `UUID.randomUUID()`, `new Random()` or `System.getenv()` in `core`.

### Tests
- Same standard as production code: readable names stating behaviour (`declinesWhenOneCentOverLimit`), arrange–act–assert, one behaviour per test.
- Builders or Object Mothers for test data; no magic numbers without a name.
- Test behaviour through public APIs, not private methods or mocks of value objects. Don't test the mock.
- Synthetic data is labelled as synthetic.
- The rest of CLAUDE.md's "Done means tested" applies too: property tests for money and the decision combiner, a Testcontainers test per DB adapter, one shared contract suite per port with more than one adapter, and the coverage and mutation floors on `core`.

### Deleting code
- Delete dead code, don't comment it out; git keeps history.
- Before deleting, find every caller (`grep`, IDE usages, reflection, config, SQL, docs) and delete or migrate them in the same change.
- Delete the tests, fixtures, config keys, migrations-not-yet-applied, docs and glossary entries that existed only for it. Never edit an applied Flyway migration; add a new one.
- Removing a public port or API is a contract change: say so in the PR and the ADR it touches.

## The refactoring discipline (refactoring.guru, Fowler)
- **When:** rule of three (the third time you copy, extract); when adding a feature is hard; when fixing a bug in code you can't read; during review.
- **How:** tiny steps, each one leaving the tests green; commit at green; never refactor and add behaviour in one step.
- **Preconditions:** tests exist and pass. If the code has none, write characterisation tests first.
- **When not:** code being deleted anyway, or code that must be rewritten from scratch (then say so and plan it).
- Leave the code a little cleaner than you found it, inside the scope of your task. Larger clean-ups get their own backlog item.

## Done checklist
- [ ] Tests first, with the edge cases, property, Testcontainers and contract tests where CLAUDE.md asks for them; all green; the build passes formatting, static analysis and warnings.
- [ ] No smell from [code-smells.md](references/code-smells.md) introduced; any you found and left are noted in the PR.
- [ ] Names match the glossary; no primitives for domain concepts; no `null` from public methods.
- [ ] No duplication of knowledge; no speculative generality; no dead code.
- [ ] Errors named, none swallowed; no PII or secrets in messages or logs.
- [ ] Money, FX, untrusted text, caution-only and core-purity rules hold.
- [ ] Refactoring and behaviour change in separate commits.
- [ ] Anything deleted took its callers, tests, config and docs with it.
