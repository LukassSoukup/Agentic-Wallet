# Refactoring techniques

The refactoring.guru catalogue ([techniques](https://refactoring.guru/refactoring/techniques)), grouped as there, each with a one-line "use when" of our own. Click a name for the steps. Every technique is a behaviour-preserving change done in small steps with the tests green after each one.

Marked ★: the ones we expect to use most in this codebase.

## Composing methods
- ★ [Extract Method](https://refactoring.guru/extract-method): a fragment can be named; the most common cure for Long Method and Comments.
- [Inline Method](https://refactoring.guru/inline-method): the body says it as clearly as the name.
- ★ [Extract Variable](https://refactoring.guru/extract-variable): an expression is hard to read; name its parts.
- [Inline Temp](https://refactoring.guru/inline-temp): a temp only holds a simple expression and gets in the way.
- [Replace Temp with Query](https://refactoring.guru/replace-temp-with-query): a temp holds a computed value that other methods also need.
- [Split Temporary Variable](https://refactoring.guru/split-temporary-variable): one local is reused for different meanings.
- [Remove Assignments to Parameters](https://refactoring.guru/remove-assignments-to-parameters): a parameter is reassigned; use a local (parameters are effectively final here).
- [Replace Method with Method Object](https://refactoring.guru/replace-method-with-method-object): a long method with tangled locals that Extract Method can't separate.
- [Substitute Algorithm](https://refactoring.guru/substitute-algorithm): a clearer algorithm does the same job.

## Moving features between objects
- ★ [Move Method](https://refactoring.guru/move-method): a method uses another class more than its own (Feature Envy).
- [Move Field](https://refactoring.guru/move-field): a field is used more by another class.
- ★ [Extract Class](https://refactoring.guru/extract-class): one class does the work of two.
- [Inline Class](https://refactoring.guru/inline-class): a class does almost nothing.
- [Hide Delegate](https://refactoring.guru/hide-delegate): clients walk a chain through your object.
- [Remove Middle Man](https://refactoring.guru/remove-middle-man): a class only forwards.
- [Introduce Foreign Method](https://refactoring.guru/introduce-foreign-method): a library class lacks one method you need.
- [Introduce Local Extension](https://refactoring.guru/introduce-local-extension): a library class lacks several; here prefer a wrapper behind a port.

## Organizing data
- [Self Encapsulate Field](https://refactoring.guru/self-encapsulate-field): subclasses or lazy logic need to intercept field access.
- ★ [Replace Data Value with Object](https://refactoring.guru/replace-data-value-with-object): a primitive carries domain meaning (`Money`, `AgentId`).
- [Change Value to Reference](https://refactoring.guru/change-value-to-reference): many equal copies should be one shared entity.
- [Change Reference to Value](https://refactoring.guru/change-reference-to-value): a small reference object should be an immutable value (`record`).
- [Replace Array with Object](https://refactoring.guru/replace-array-with-object): an array holds different kinds of things by position.
- [Duplicate Observed Data](https://refactoring.guru/duplicate-observed-data): domain data is stuck in UI code.
- [Change Unidirectional Association to Bidirectional](https://refactoring.guru/change-unidirectional-association-to-bidirectional): rarely; prefer one direction.
- [Change Bidirectional Association to Unidirectional](https://refactoring.guru/change-bidirectional-association-to-unidirectional): one side doesn't need the other.
- ★ [Replace Magic Number with Symbolic Constant](https://refactoring.guru/replace-magic-number-with-symbolic-constant): a literal has meaning (scale `2`, `RoundingMode.HALF_EVEN`).
- [Encapsulate Field](https://refactoring.guru/encapsulate-field): a public field.
- ★ [Encapsulate Collection](https://refactoring.guru/encapsulate-collection): a getter returns the internal mutable collection.
- ★ [Replace Type Code with Class](https://refactoring.guru/replace-type-code-with-class): string/int codes stand for a closed set (use an enum or sealed type).
- [Replace Type Code with Subclasses](https://refactoring.guru/replace-type-code-with-subclasses): the code changes behaviour and never changes at runtime.
- [Replace Type Code with State/Strategy](https://refactoring.guru/replace-type-code-with-state-strategy): the code changes behaviour and can change at runtime.
- [Replace Subclass with Fields](https://refactoring.guru/replace-subclass-with-fields): subclasses differ only in constant data.

## Simplifying conditional expressions
- ★ [Decompose Conditional](https://refactoring.guru/decompose-conditional): a complex condition; name the condition and each branch.
- [Consolidate Conditional Expression](https://refactoring.guru/consolidate-conditional-expression): several conditions lead to the same result.
- [Consolidate Duplicate Conditional Fragments](https://refactoring.guru/consolidate-duplicate-conditional-fragments): the same code in every branch.
- [Remove Control Flag](https://refactoring.guru/remove-control-flag): a boolean steers a loop; use `return`/`break`.
- ★ [Replace Nested Conditional with Guard Clauses](https://refactoring.guru/replace-nested-conditional-with-guard-clauses): the happy path is buried in nesting.
- ★ [Replace Conditional with Polymorphism](https://refactoring.guru/replace-conditional-with-polymorphism): the same switch on type in several places.
- [Introduce Null Object](https://refactoring.guru/introduce-null-object): many `null` checks for the same absent thing.
- [Introduce Assertion](https://refactoring.guru/introduce-assertion): code assumes something; here prefer a typed value or a precondition that throws a named exception (Java `assert` is off in production).

## Simplifying method calls
- ★ [Rename Method](https://refactoring.guru/rename-method): the name doesn't say what it does.
- [Add Parameter](https://refactoring.guru/add-parameter) / [Remove Parameter](https://refactoring.guru/remove-parameter): the method needs more data / no longer uses some.
- ★ [Separate Query from Modifier](https://refactoring.guru/separate-query-from-modifier): a method both returns a value and changes state.
- [Parameterize Method](https://refactoring.guru/parameterize-method): several methods differ only in a value.
- [Replace Parameter with Explicit Methods](https://refactoring.guru/replace-parameter-with-explicit-methods): a parameter selects which branch runs (flag argument).
- [Preserve Whole Object](https://refactoring.guru/preserve-whole-object): you pull several values from an object to pass them on.
- [Replace Parameter with Method Call](https://refactoring.guru/replace-parameter-with-method-call): the callee could get the value itself.
- ★ [Introduce Parameter Object](https://refactoring.guru/introduce-parameter-object): a group of parameters travels together.
- [Remove Setting Method](https://refactoring.guru/remove-setting-method): a field should be set only at construction.
- [Hide Method](https://refactoring.guru/hide-method): nobody outside uses it.
- ★ [Replace Constructor with Factory Method](https://refactoring.guru/replace-constructor-with-factory-method): construction needs a name, validation or a choice of type (`Money.of`, `UntrustedText.from`).
- ★ [Replace Error Code with Exception](https://refactoring.guru/replace-error-code-with-exception): a method returns `-1`/`null` for failure; throw a named exception (but expected business outcomes stay values).
- [Replace Exception with Test](https://refactoring.guru/replace-exception-with-test): an exception is thrown where a simple check would do.

## Dealing with generalization
- [Pull Up Field](https://refactoring.guru/pull-up-field) / [Pull Up Method](https://refactoring.guru/pull-up-method) / [Pull Up Constructor Body](https://refactoring.guru/pull-up-constructor-body): subclasses duplicate it.
- [Push Down Field](https://refactoring.guru/push-down-field) / [Push Down Method](https://refactoring.guru/push-down-method): only one subclass uses it.
- [Extract Subclass](https://refactoring.guru/extract-subclass): some features are used only in some cases.
- [Extract Superclass](https://refactoring.guru/extract-superclass): two classes share features (consider composition first).
- ★ [Extract Interface](https://refactoring.guru/extract-interface): clients use a subset of a class, or you need a port.
- [Collapse Hierarchy](https://refactoring.guru/collapse-hierarchy): a subclass is practically its parent.
- [Form Template Method](https://refactoring.guru/form-template-method): subclasses run the same steps with different details.
- ★ [Replace Inheritance with Delegation](https://refactoring.guru/replace-inheritance-with-delegation): a subclass uses only part of its parent (Refused Bequest).
- [Replace Delegation with Inheritance](https://refactoring.guru/replace-delegation-with-inheritance): a class delegates everything to one object; rarely right here.

## Beyond the catalogue
Fowler's 2nd edition adds techniques worth knowing: Combine Functions into Class, Split Phase (parse → decide → act; fits the decision flow), Replace Loop with Pipeline (streams, when clearer), Replace Primitive with Object, Introduce Special Case, Slide Statements, and Remove Dead Code.
