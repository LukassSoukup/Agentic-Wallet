---
name: design-patterns
description: When and how to use design patterns in this repo, from refactoring.guru (the 22 GoF patterns, with Java and TypeScript examples) plus DDD tactical patterns and hexagonal ports and adapters. Use PROACTIVELY, without being asked, whenever you write, change, refactor, review or delete code here that adds, reshapes or removes types, interfaces, ports, adapters, rules or object creation, and before introducing or removing any pattern.
---

# Design patterns

A pattern is a named, proven answer to a recurring design problem. Use one when the problem is in front of you, not because the code might need it someday. Pair this skill with [clean-code](../clean-code/SKILL.md): most patterns arrive through a refactoring that removes a smell.

Sources: [refactoring.guru design patterns](https://refactoring.guru/design-patterns) and its example code for [Java](https://github.com/RefactoringGuru/design-patterns-java) and [TypeScript](https://github.com/RefactoringGuru/design-patterns-typescript); Gamma et al., *Design Patterns* (GoF); Bloch, *Effective Java*; Evans, *Domain-Driven Design*; Cockburn, hexagonal architecture; Fowler, *Patterns of Enterprise Application Architecture*. The project's [CLAUDE.md](../../../CLAUDE.md) and ADRs win over this skill.

**Licence:** the refactoring.guru example repositories are CC BY-NC-ND 4.0 (non-commercial, no derivatives) and the site text is copyrighted. Read them to understand a pattern; never copy or adapt their code or text into this repo. Write our own, test-first, to the quality bar.

## When to use it
Before you add an interface, abstract class, factory, wrapper, event, or a second implementation of anything; when a smell in [clean-code](../clean-code/references/code-smells.md) points at a pattern (Switch Statements → Strategy/State, Long Parameter List → Builder, Incomplete Library Class → Adapter); when you review or delete code that uses one.

## Workflow
1. **State the problem in one sentence**, in domain words, without naming a pattern. ("Each decision rule must be testable alone and new rules must not change the combiner.")
2. **Check the simplest thing first.** A function, a `record`, an enum, a sealed interface with a `switch`, or a lambda often solves it. Java 25 and TypeScript make several GoF patterns a few lines (Strategy is a functional interface; Visitor is often a `switch` over a sealed type; Iterator is built in).
3. **Pick the pattern** from the table below or [catalog.md](references/catalog.md). Read its refactoring.guru page and the matching example in the Java or TypeScript repo.
4. **Name it in the code** using domain words, not pattern words: `DecisionRule`, not `RuleStrategy`; `AuditingSpendLedger`, not `SpendLedgerDecorator`. Mention the pattern in the class Javadoc only if it helps a reader.
5. **Test it through its role:** one contract test suite per port, run against every adapter (quality bar); each strategy/state tested alone; decorators tested for what they add and that they delegate.
6. **Record a significant choice** (a new port, a pattern that shapes a module) in the ADR for that area.

## Problem → pattern
| You need to… | Consider | Instead of |
|---|---|---|
| Swap an algorithm or rule set behind one interface | **Strategy** | `if`/`switch` on a mode flag |
| Change behaviour as a lifecycle moves (proposed → stepped-up → approved/declined → authorised → captured) | **State**, or a sealed type of states with transition methods | Status strings and scattered checks |
| Run a sequence of independent checks, each able to stop or annotate | **Chain of Responsibility**, or a list of rules folded by a combiner | One long method |
| Talk to an external system (DB, rail, FX feed, LLM) without the core knowing it | **Adapter** behind a port (hexagonal) | Framework or SDK types in the core |
| Add auditing, idempotency, retries, metrics or caching around a port | **Decorator** | Editing every adapter |
| Give a subsystem one simple entry point | **Facade** (an application service) | Callers orchestrating many ports |
| Control access to or lazily create something expensive or remote | **Proxy** | Checks spread through callers |
| Build a complex immutable value or test fixture | **Builder** | Telescoping constructors |
| Create one of several types from input, with validation | **Factory Method** / static factory | `new` + `if` at every call site |
| Create families of related objects (simulated vs live rail clients) | **Abstract Factory**, usually just DI wiring | Mixing families by accident |
| Represent an operation as data to queue, log, replay or undo | **Command** | Direct calls that leave no record |
| Notify other parts after something happened | **Observer** / domain events | The producer calling every consumer |
| Coordinate many objects without them knowing each other | **Mediator** | A web of references |
| Treat a tree of parts and wholes the same way (order → items, nested budgets) | **Composite** | Type checks for leaf vs group |
| Snapshot state to restore it | **Memento** (here usually an immutable version) | Exposing internals |
| Fixed steps with varying details | **Template Method**, but prefer Strategy (composition) | Copy-pasted procedures |
| New operations over a stable, closed type hierarchy | **Visitor**, or a `switch` over a sealed type | `instanceof` chains |
| Absence without `null` checks | **Null Object** / `Optional` | `null` returns |

## Patterns as they fit this project
Illustrative, based on the draft model in [ADR-0006](../../../docs/adr/ADR-0006-domain-model-and-ports.md); TASK-003 and later ADRs decide the real shapes.

- **Ports and adapters (hexagonal).** Ports are interfaces in `core` with contract comments; adapters live outside and are Adapters in the GoF sense. One shared contract test suite per port.
- **Strategy for decision rules.** Each fixed rule is its own type behind one interface, returning a verdict with reasons. The combiner folds verdicts and can only raise caution (APPROVE < STEP_UP < DECLINE). A risk or model signal is just another input to that fold; no strategy can lower the outcome (invariant 2).
- **Value objects** (DDD): `Money`, `IdempotencyKey`, `UntrustedText`, `Period`. Immutable `record`s, validated in the factory.
- **Aggregates and repositories** (DDD): one aggregate per consistency boundary; repositories are ports, one per aggregate root.
- **Decorator for cross-cutting port behaviour:** an idempotency decorator around the decision use case; an auditing decorator that writes every decision to the append-only log. They must not swallow failures.
- **Command + append-only log for replay** (invariant 8): the decision's inputs are stored as data so the decision can be recomputed.
- **State (or a sealed state type) for lifecycles:** proposals, step-ups, authorisations, mismatch cases. Illegal transitions are unrepresentable or throw a named exception.
- **Factory methods at the trust boundary:** untrusted input becomes a validated value or is rejected (parse, don't validate).
- **Builder / Object Mother for tests:** readable fixtures with labelled synthetic data.

## Anti-patterns and red lines
- **Singleton and other global state are banned in `core`** (invariant 9): no static clocks, random sources, registries or caches. Let the DI container own lifetimes in adapters; pass dependencies in through constructors.
- **Service Locator** in place of constructor injection.
- **Speculative patterns:** a factory with one product, a strategy with one implementation and no second in sight, an observer with one listener. Wait for the second real case (rule of three).
- **Pattern names as class names** (`AbstractDecisionStrategyFactoryImpl`).
- **Anemic domain model:** aggregates with only getters and all rules in "services".
- **Leaky adapters:** a JPA entity, HTTP DTO or SDK type crossing into `core`.
- **Inheritance for reuse:** prefer composition and delegation; deep hierarchies hide behaviour.
- **Observers with ordering or failure assumptions:** if a consumer must run, or run first, call it explicitly.
- **Clever over clear:** if a teammate needs the pattern's name to understand the code, the code is too clever.

## Removing a pattern
Patterns are not permanent. When the variation they served is gone (one strategy left, a decorator that adds nothing, a factory with one product), collapse them: Inline Class, Collapse Hierarchy, Remove Middle Man. Delete the tests and wiring that went with them, and update the ADR if it named the pattern.

## Done checklist
- [ ] The problem is stated in domain words, and a simpler construct was considered first.
- [ ] The pattern fits a real, present variation; no speculative generality.
- [ ] Names are domain names; the core imports no framework, DB, HTTP, clock or random.
- [ ] Every port has a contract comment and one contract test suite run against each adapter.
- [ ] No singleton or global state; dependencies come in through constructors.
- [ ] Nothing copied or adapted from the CC BY-NC-ND example repos.
- [ ] A choice that shapes a module is recorded in an ADR.
