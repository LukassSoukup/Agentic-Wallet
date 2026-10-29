# Refactoring techniques

The refactoring.guru catalogue ([techniques](https://refactoring.guru/refactoring/techniques)), grouped as there, each with a one-line "use when" of our own. The summary is usually enough. Only if you're unsure how to carry a technique out, open `https://refactoring.guru/<name>`: the name in lowercase, with spaces and `/` replaced by `-` (e.g. `replace-type-code-with-state-strategy`). Every technique is a behaviour-preserving change done in small steps with the tests green after each one.

Marked ★: the ones we expect to use most in this codebase.

## Composing methods
- ★ Extract Method: a fragment can be named; the most common cure for Long Method and Comments.
- Inline Method: the body says it as clearly as the name.
- ★ Extract Variable: an expression is hard to read; name its parts.
- Inline Temp: a temp only holds a simple expression and gets in the way.
- Replace Temp with Query: a temp holds a computed value that other methods also need.
- Split Temporary Variable: one local is reused for different meanings.
- Remove Assignments to Parameters: a parameter is reassigned; use a local (parameters are effectively final here).
- Replace Method with Method Object: a long method with tangled locals that Extract Method can't separate.
- Substitute Algorithm: a clearer algorithm does the same job.

## Moving features between objects
- ★ Move Method: a method uses another class more than its own (Feature Envy).
- Move Field: a field is used more by another class.
- ★ Extract Class: one class does the work of two.
- Inline Class: a class does almost nothing.
- Hide Delegate: clients walk a chain through your object.
- Remove Middle Man: a class only forwards.
- Introduce Foreign Method: a library class lacks one method you need.
- Introduce Local Extension: a library class lacks several; here prefer a wrapper behind a port.

## Organizing data
- Self Encapsulate Field: subclasses or lazy logic need to intercept field access.
- ★ Replace Data Value with Object: a primitive carries domain meaning (`Money`, `AgentId`).
- Change Value to Reference: many equal copies should be one shared entity.
- Change Reference to Value: a small reference object should be an immutable value (`record`).
- Replace Array with Object: an array holds different kinds of things by position.
- Duplicate Observed Data: domain data is stuck in UI code.
- Change Unidirectional Association to Bidirectional: rarely; prefer one direction.
- Change Bidirectional Association to Unidirectional: one side doesn't need the other.
- ★ Replace Magic Number with Symbolic Constant: a literal has meaning (scale `2`, `RoundingMode.HALF_EVEN`).
- Encapsulate Field: a public field.
- ★ Encapsulate Collection: a getter returns the internal mutable collection.
- ★ Replace Type Code with Class: string/int codes stand for a closed set (use an enum or sealed type).
- Replace Type Code with Subclasses: the code changes behaviour and never changes at runtime.
- Replace Type Code with State/Strategy: the code changes behaviour and can change at runtime.
- Replace Subclass with Fields: subclasses differ only in constant data.

## Simplifying conditional expressions
- ★ Decompose Conditional: a complex condition; name the condition and each branch.
- Consolidate Conditional Expression: several conditions lead to the same result.
- Consolidate Duplicate Conditional Fragments: the same code in every branch.
- Remove Control Flag: a boolean steers a loop; use `return`/`break`.
- ★ Replace Nested Conditional with Guard Clauses: the happy path is buried in nesting.
- ★ Replace Conditional with Polymorphism: the same switch on type in several places.
- Introduce Null Object: many `null` checks for the same absent thing.
- Introduce Assertion: code assumes something; here prefer a typed value or a precondition that throws a named exception (Java `assert` is off in production).

## Simplifying method calls
- ★ Rename Method: the name doesn't say what it does.
- Add Parameter / Remove Parameter: the method needs more data / no longer uses some.
- ★ Separate Query from Modifier: a method both returns a value and changes state.
- Parameterize Method: several methods differ only in a value.
- Replace Parameter with Explicit Methods: a parameter selects which branch runs (flag argument).
- Preserve Whole Object: you pull several values from an object to pass them on.
- Replace Parameter with Method Call: the callee could get the value itself.
- ★ Introduce Parameter Object: a group of parameters travels together.
- Remove Setting Method: a field should be set only at construction.
- Hide Method: nobody outside uses it.
- ★ Replace Constructor with Factory Method: construction needs a name, validation or a choice of type (`Money.of`, `UntrustedText.from`).
- ★ Replace Error Code with Exception: a method returns `-1`/`null` for failure; throw a named exception (but expected business outcomes stay values).
- Replace Exception with Test: an exception is thrown where a simple check would do.

## Dealing with generalization
- Pull Up Field / Pull Up Method / Pull Up Constructor Body: subclasses duplicate it.
- Push Down Field / Push Down Method: only one subclass uses it.
- Extract Subclass: some features are used only in some cases.
- Extract Superclass: two classes share features (consider composition first).
- ★ Extract Interface: clients use a subset of a class, or you need a port.
- Collapse Hierarchy: a subclass is practically its parent.
- Form Template Method: subclasses run the same steps with different details.
- ★ Replace Inheritance with Delegation: a subclass uses only part of its parent (Refused Bequest).
- Replace Delegation with Inheritance: a class delegates everything to one object; rarely right here.

## Beyond the catalogue
Fowler's 2nd edition adds techniques worth knowing: Combine Functions into Class, Split Phase (parse → decide → act; fits the decision flow), Replace Loop with Pipeline (streams, when clearer), Replace Primitive with Object, Introduce Special Case, Slide Statements, and Remove Dead Code.
