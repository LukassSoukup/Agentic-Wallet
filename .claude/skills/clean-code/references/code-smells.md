# Code smells

The refactoring.guru catalogue ([smells](https://refactoring.guru/refactoring/smells)), grouped as there, with what to look for and the refactorings that usually cure it ([refactorings.md](refactorings.md)). Descriptions are our own summaries and are usually enough. Only if a smell is unclear, open `https://refactoring.guru/smells/<name>` (lowercase, spaces replaced by `-`). "Here" notes say where the smell is most likely in this project.

A smell is a hint, not a verdict. Name it, then decide.

## Bloaters
Code that has grown too big to hold in your head.

| Smell | Signs | Usual cures |
|---|---|---|
| Long Method | You scroll; you want a comment to separate the parts. | Extract Method, Replace Temp with Query, Introduce Parameter Object, Replace Method with Method Object, Decompose Conditional |
| Large Class | Many fields, many unrelated methods; the name ends in `Service` or `Manager` and does everything. | Extract Class, Extract Subclass, Extract Interface |
| Primitive Obsession | `BigDecimal amount, String currency`; `String agentId`; int codes for states. | Replace Data Value with Object, Replace Type Code with Class / Subclasses / State-Strategy, Introduce Parameter Object |
| Long Parameter List | Four or more parameters; callers pass the same group every time. | Replace Parameter with Method Call, Preserve Whole Object, Introduce Parameter Object |
| Data Clumps | The same few fields or parameters always appear together. | Extract Class, Introduce Parameter Object, Preserve Whole Object |

Here: amount + currency → `Money`; agent + mandate + period → a limit context; decision outcome + reasons + rule results → `Decision`.

## Object-orientation abusers
OO features used half-way or the wrong way.

| Smell | Signs | Usual cures |
|---|---|---|
| Switch Statements | The same `switch`/`if` chain on a type code in several places. | Replace Conditional with Polymorphism, Replace Type Code with State/Strategy, Replace Parameter with Explicit Methods, Introduce Null Object |
| Temporary Field | Fields that are set only for one algorithm and are `null` otherwise. | Extract Class, Replace Method with Method Object, Introduce Null Object |
| Refused Bequest | A subclass ignores or throws on inherited methods. | Replace Inheritance with Delegation, Extract Superclass |
| Alternative Classes with Different Interfaces | Two classes do the same job with different method names. | Rename Method, Move Method, Add/Parameterize, Extract Superclass |

Here: an exhaustive `switch` over a **sealed** type or enum (`Outcome`) in one place is fine in modern Java; the smell is the *same* switch repeated across classes.

## Change preventers
One change forces many edits.

| Smell | Signs | Usual cures |
|---|---|---|
| Divergent Change | One class changes for many unrelated reasons (new rail *and* new rule *and* new DB column). | Extract Class, Extract Superclass/Subclass |
| Shotgun Surgery | One change touches many classes a little. | Move Method, Move Field, Inline Class |
| Parallel Inheritance Hierarchies | Adding a subclass here forces a subclass there. | Move Method, Move Field |

Here: adding a decision rule should mean one new rule class plus its tests, not edits to the combiner, the audit writer and the API mapper.

## Dispensables
Things whose absence would make the code clearer.

| Smell | Signs | Usual cures |
|---|---|---|
| Comments | A comment explains what a block does. | Extract Variable, Extract Method, Rename Method, Introduce Assertion |
| Duplicate Code | The same knowledge in two places (not just similar-looking lines). | Extract Method, Pull Up Method, Form Template Method, Substitute Algorithm, Extract Class |
| Lazy Class | A class that does too little to justify itself. | Inline Class, Collapse Hierarchy |
| Data Class | Only fields and accessors; all behaviour lives elsewhere. | Move Method, Encapsulate Field/Collection, Remove Setting Method, Hide Method |
| Dead Code | Unused variables, parameters, methods, classes, branches. | Delete it (Inline Class, Collapse Hierarchy, Remove Parameter) |
| Speculative Generality | Abstract classes, hooks or parameters "for later". | Collapse Hierarchy, Inline Class, Inline Method, Remove Parameter |

Here: contract comments on ports are required, not a smell. A `record` used as a DTO at an adapter edge is not a Data Class; a domain aggregate with only getters is.

## Couplers
Classes that know too much about each other.

| Smell | Signs | Usual cures |
|---|---|---|
| Feature Envy | A method uses another object's data more than its own. | Move Method, Extract Method |
| Inappropriate Intimacy | A class reaches into another's internals. | Move Method/Field, Extract Class, Hide Delegate, Change Bidirectional Association to Unidirectional, Replace Inheritance with Delegation |
| Message Chains | `a.getB().getC().getD()`. | Hide Delegate, Extract Method, Move Method |
| Middle Man | A class that only forwards calls. | Remove Middle Man, Inline Method |

Here: adapters are thin by design, so a thin port adapter is not a Middle Man. A core class that knows a JPA entity or an HTTP DTO is Inappropriate Intimacy and breaks invariant 9.

## Other smells

| Smell | Signs | Usual cures |
|---|---|---|
| Incomplete Library Class | A library lacks what you need and you can't change it. | Introduce Foreign Method, Introduce Local Extension (in practice: wrap it in an adapter) |

## Project-specific smells (beyond the catalogue)
- `double`/`float`/JS `number` holding money; `equals` on `BigDecimal`.
- `Instant.now()`, `UUID.randomUUID()`, `Random`, `System.getenv()` or a framework annotation inside `core`.
- Untrusted text compared, parsed or branched on outside the intent check and the suspicious-text detector (both may only raise caution).
- A model or risk signal whose result can lower an outcome.
- An exception used for an expected business outcome, or a `catch` that hides one.
- Audit rows built from data other than the decision's actual inputs.
