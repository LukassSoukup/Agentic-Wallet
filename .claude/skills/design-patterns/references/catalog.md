# Pattern catalogue

The 22 patterns on [refactoring.guru](https://refactoring.guru/design-patterns/catalog), grouped as there. For each: links to the explanation and to the Java and TypeScript examples, then our own notes on when to use it, when not, and how it fits this project. The "Here" notes are illustrative until TASK-003 settles the model.

The example repositories are CC BY-NC-ND 4.0: read them, never copy or adapt their code into this repo. In the Java repo each pattern has an `example` package; in the TypeScript repo, `Conceptual` and often `RealWorld` folders.

## Creational

### Factory Method
[Explanation](https://refactoring.guru/design-patterns/factory-method) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/factory_method) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/FactoryMethod)
- **Use when:** Creation needs a name, validation, caching, or a choice of subtype.
- **Avoid when:** Plain `new` of one concrete type is clear enough.
- **Here:** Static factories on value objects (`Money.of`, `UntrustedText.from`) at the trust boundary.

### Abstract Factory
[Explanation](https://refactoring.guru/design-patterns/abstract-factory) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/abstract_factory) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/AbstractFactory)
- **Use when:** Whole families of related objects must be created consistently.
- **Avoid when:** There is only one family; DI configuration already does it.
- **Here:** Simulated vs live rail clients chosen by configuration; usually just Spring profiles wiring adapters.

### Builder
[Explanation](https://refactoring.guru/design-patterns/builder) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/builder) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Builder)
- **Use when:** An object has many optional parts or must be built in steps, then be immutable.
- **Avoid when:** A `record` with a few required fields.
- **Here:** Test data builders; complex request/decision values. Validate in `build()`.

### Prototype
[Explanation](https://refactoring.guru/design-patterns/prototype) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/prototype) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Prototype)
- **Use when:** Copying a configured object is cheaper or simpler than building it.
- **Avoid when:** Immutable values (just share them) or a `with`-style copy on a record.
- **Here:** Rare. Prefer immutable values and copy-with methods.

### Singleton
[Explanation](https://refactoring.guru/design-patterns/singleton) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/singleton) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Singleton)
- **Use when:** Almost never: a single instance managed by the DI container is not a GoF Singleton.
- **Avoid when:** Always in `core` (invariant 9). Hidden global state breaks tests and replay.
- **Here:** Banned in `core`. Let the container own lifetimes in adapters.

## Structural

### Adapter
[Explanation](https://refactoring.guru/design-patterns/adapter) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/adapter) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Adapter)
- **Use when:** Code must talk to an interface it doesn't match (DB, rail SDK, FX feed, LLM).
- **Avoid when:** You control both sides; change one instead.
- **Here:** Every outbound port implementation. Translate types at the edge; no business rules inside.

### Bridge
[Explanation](https://refactoring.guru/design-patterns/bridge) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/bridge) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Bridge)
- **Use when:** Two dimensions vary independently (abstraction × implementation).
- **Avoid when:** Only one dimension varies.
- **Here:** Possibly payment method × rail; decide in TASK-003/007, not speculatively.

### Composite
[Explanation](https://refactoring.guru/design-patterns/composite) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/composite) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Composite)
- **Use when:** Parts and wholes form a tree and clients should treat them alike.
- **Avoid when:** The structure is flat.
- **Here:** Orders with items and split orders; composite rules (all-of / any-of) if mandates need them.

### Decorator
[Explanation](https://refactoring.guru/design-patterns/decorator) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/decorator) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Decorator)
- **Use when:** Add responsibilities to an object without changing it or its callers.
- **Avoid when:** The added behaviour is core logic, or the order of wrappers matters invisibly.
- **Here:** Metrics, retries, caching around ports. Not for the idempotency lookup or the audit write: those are steps in the decision flow (ADR-0006). Never swallow failures.

### Facade
[Explanation](https://refactoring.guru/design-patterns/facade) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/facade) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Facade)
- **Use when:** Callers need one simple entry to a subsystem.
- **Avoid when:** It turns into a god object that owns logic.
- **Here:** Application services/use cases (e.g. "decide on a purchase") orchestrating ports; logic stays in the domain.

### Flyweight
[Explanation](https://refactoring.guru/design-patterns/flyweight) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/flyweight) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Flyweight)
- **Use when:** Huge numbers of similar objects exhaust memory.
- **Avoid when:** Memory isn't a measured problem.
- **Here:** Unlikely; measure first (the Pi has 16 GB).

### Proxy
[Explanation](https://refactoring.guru/design-patterns/proxy) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/proxy) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Proxy)
- **Use when:** Control access to an object: lazy creation, remote calls, caching, access checks.
- **Avoid when:** A decorator (adds behaviour) or adapter (changes interface) is what you mean.
- **Here:** Caching FX rates behind the `FxRates` port; access checks at the API edge.

## Behavioral

### Chain of Responsibility
[Explanation](https://refactoring.guru/design-patterns/chain-of-responsibility) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/chain_of_responsibility) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/ChainOfResponsibility)
- **Use when:** A request passes through handlers, any of which may handle or stop it.
- **Avoid when:** Every handler must run; then use a list and a fold.
- **Here:** Inbound request filters. For decision rules prefer "run all rules, combine verdicts" so every reason is recorded.

### Command
[Explanation](https://refactoring.guru/design-patterns/command) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/command) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Command)
- **Use when:** An operation must be stored, queued, logged, replayed or undone.
- **Avoid when:** A direct call with no need for a record.
- **Here:** Decision inputs as data for the append-only audit log and replay (invariant 8); idempotent request handling.

### Iterator
[Explanation](https://refactoring.guru/design-patterns/iterator) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/iterator) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Iterator)
- **Use when:** Traverse a custom collection without exposing its structure.
- **Avoid when:** A standard collection or stream does it.
- **Here:** Built into Java and TS; write one only for paged adapter reads.

### Mediator
[Explanation](https://refactoring.guru/design-patterns/mediator) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/mediator) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Mediator)
- **Use when:** Many objects interact and direct references form a web.
- **Avoid when:** Two or three collaborators.
- **Here:** The use-case service often plays this role; keep it thin.

### Memento
[Explanation](https://refactoring.guru/design-patterns/memento) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/memento) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Memento)
- **Use when:** Capture and restore state without exposing internals.
- **Avoid when:** Immutable versions already give you history.
- **Here:** Mandate versions are immutable snapshots; that is the memento.

### Observer
[Explanation](https://refactoring.guru/design-patterns/observer) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/observer) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Observer)
- **Use when:** Others must react to a change without the source knowing them.
- **Avoid when:** A consumer must run, run in order, or its failure matters; call it explicitly.
- **Here:** Domain events after a decision is recorded (notifications, read models). Never for the audit write itself.

### State
[Explanation](https://refactoring.guru/design-patterns/state) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/state) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/State)
- **Use when:** Behaviour depends on a state that changes at runtime, with rules on transitions.
- **Avoid when:** Two states and one check.
- **Here:** Purchase, step-up, authorisation and mismatch-case lifecycles; a sealed interface of states works well in Java 25.

### Strategy
[Explanation](https://refactoring.guru/design-patterns/strategy) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/strategy) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Strategy)
- **Use when:** Interchangeable algorithms behind one interface, chosen at runtime or by configuration.
- **Avoid when:** Only one algorithm exists.
- **Here:** Each fixed decision rule; routing policy; rounding/FX policy. A functional interface or lambda is enough when there is no state.

### Template Method
[Explanation](https://refactoring.guru/design-patterns/template-method) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/template_method) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/TemplateMethod)
- **Use when:** Fixed steps with a few varying details across subclasses.
- **Avoid when:** Composition (Strategy) does it without inheritance; usually prefer that.
- **Here:** Rare; possibly an abstract contract test suite that each adapter's test extends.

### Visitor
[Explanation](https://refactoring.guru/design-patterns/visitor) · [Java example](https://github.com/RefactoringGuru/design-patterns-java/tree/main/src/refactoring_guru/visitor) · [TypeScript example](https://github.com/RefactoringGuru/design-patterns-typescript/tree/main/src/Visitor)
- **Use when:** Add operations to a stable, closed hierarchy without changing it.
- **Avoid when:** The hierarchy changes often, or a `switch` over a sealed type is clearer.
- **Here:** Prefer pattern-matching `switch` over sealed types (e.g. rendering decision reasons).

## Beyond GoF
Patterns from other well-known sources that this project leans on. refactoring.guru doesn't cover them; follow the references.

- **Ports and Adapters / Hexagonal** (Cockburn): the core defines ports; adapters implement them outside. Invariant 9.
- **Value Object, Entity, Aggregate, Repository, Domain Event, Domain Service** (Evans, *DDD*): the tactical building blocks of `core`.
- **Specification** (Evans & Fowler): a composable, named predicate; an option for mandate rules that combine with and/or/not.
- **Null Object / Special Case** (Fowler, *Refactoring*): an object that stands for "none" with safe behaviour.
- **Unit of Work, Data Mapper, Identity Map** (Fowler, *PoEAA*): what JPA does for you; keep them in adapters.
- **Idempotent Receiver** (Hohpe & Woolf, *Enterprise Integration Patterns*): store the key and the result, return the stored result on redelivery. Invariant 7.
- **Transactional Outbox** (Richardson, *Microservices Patterns*): publish events reliably together with the state change, if events leave the process.
- **Parse, don't validate** (Alexis King) and **make illegal states unrepresentable** (Yaron Minsky): turn input into types that can only be valid.
