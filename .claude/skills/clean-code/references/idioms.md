# Idioms

Write each language the way its own experienced developers do, not another language's style translated into it. Where an idiom conflicts with a domain invariant (JS `number` for money, say), the invariant wins. Where the surrounding code already has a settled convention, match it. Stack per [ADR-0001](../../../../docs/adr/ADR-0001-tech-stack.md) until T-004 revises it.

Sources: Bloch, *Effective Java* (3rd ed.); the Java Language Specification and JEPs; the TypeScript Handbook and typescript-eslint's `strict-type-checked` config (type-aware rules such as `no-floating-promises` are not in plain `recommended`); the PostgreSQL manual; PEP 8 and PEP 20.

## Java 25
- **Values are `record`s.** Validate and normalise in the compact constructor (`Objects.requireNonNull`, scale and range checks); equals/hashCode come free, but a `BigDecimal` component compares by scale (`10.0` ≠ `10.00`), so `Money` fixes the scale in its compact constructor to keep record equality in line with `compareTo`.
- **Closed sets are enums or `sealed` interfaces.** Branch with a `switch` *expression*, or a pattern `switch` over a sealed type, that covers every case with no `default`: then a new case is a compile error. A plain `switch` *statement* over an enum is not checked for exhaustiveness, so don't use one for decision outcomes.
- **Pattern matching** over casts: `if (x instanceof Money m)`, record deconstruction patterns in `switch`.
- **Static factories** named `of`, `from`, `parse`, `valueOf` over public constructors when creation validates, caches or picks a subtype.
- **Immutable collections:** `List.of`, `Map.of`, `List.copyOf` for defensive copies; `Stream.toList()`. `EnumMap`/`EnumSet` for enum keys.
- **`Optional` only as a return type**: never as a field, parameter or collection element. Use `orElseThrow`, `map`, `ifPresentOrElse`, never a bare `get()`.
- **`var`** only when the type is obvious from the right-hand side.
- **Streams** when they read better than a loop; no side effects inside them; a plain loop for two lines or when you need early exit with state.
- **`BigDecimal`:** build from `String` or `BigDecimal.valueOf(long, scale)`, never `new BigDecimal(double)`; `setScale(2, RoundingMode.HALF_EVEN)`; `compareTo` for equality; keep it behind `Money`.
- **`java.time`** for time, taken from the `Clock` port; `Instant` for moments, `LocalDate` plus zone for budget periods. Never `Date`/`Calendar`.
- **try-with-resources** for anything `AutoCloseable`.
- **Text blocks** for multi-line SQL, JSON fixtures and prompts.
- **Enums can carry behaviour** (a method per constant) when the behaviour is small and closed.
- **Concurrency:** virtual threads for blocking I/O in adapters; no shared mutable state; no preview features without an ADR.
- **Spring Boot 4 (adapters only):** constructor injection with `final` fields, never field injection; `@ConfigurationProperties` bound to records; thin controllers that map and delegate. Boot 4 and Jackson 3 are newer than most training data: check the current docs before relying on memory of an API.

## TypeScript (UI)
- `strict` on. No `any`: use `unknown` at boundaries and narrow it (or parse with a schema).
- **Discriminated unions** for closed sets, with an exhaustive `switch` ending in a `never` check.
- `const` by default, `let` when reassigned, never `var`. `readonly` properties and `ReadonlyArray`; `as const` for literal tables.
- `===` only; `?.` and `??` instead of `&&` chains and `||` defaults (`||` treats `0` and `""` as missing).
- `async`/`await` over `.then` chains; no floating promises; `try`/`catch` around awaited calls that can fail, with a named error.
- **Money is never a `number`:** keep the API's decimal string, or use a decimal library; format with `Intl.NumberFormat`.
- ES modules, named exports, one concern per module.
- React: function components and hooks; derive values instead of storing them in state; stable `key`s from ids, never array indexes for dynamic lists.

## SQL (PostgreSQL)
- Money in `numeric(p, 2)` with explicit precision, never `float`/`real`/`money`; times in `timestamptz`.
- Invariants in the schema too: `NOT NULL`, `CHECK`, `UNIQUE`, foreign keys. The idempotency key has a unique constraint.
- Explicit column lists in `SELECT` and `INSERT`; no `SELECT *` in application code.
- Bind parameters only; never build SQL from strings.
- `snake_case` identifiers; plural or singular table names, but one convention throughout.
- Migrations only move forward; an applied migration is never edited. The audit table is insert-only by grants and trigger.

## Python (scripts)
- PEP 8; type hints on functions; `pathlib.Path` over string paths; `with` for files.
- f-strings; `argparse` for CLIs; `if __name__ == "__main__":` entry point; standard library first.
- `decimal.Decimal` for money, built from `str`, never from `float`, and quantized with `ROUND_HALF_EVEN`; no bare `except:`; exit non-zero on failure with a clear message.
