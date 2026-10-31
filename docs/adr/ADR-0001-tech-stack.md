---
id: ADR-0001
status: superseded by ADR-0009
date: 2026-10-17
tasks: []
---
# ADR-0001: Tech stack: Java 25, Spring Boot 4, PostgreSQL with plain SQL, React + TypeScript UI

> **Superseded on 2026-10-31 by [ADR-0009](ADR-0009-tech-stack-revision.md)** (T-004): it was decided for a 26-hour hackathon and re-opened on 2026-10-23 for this revision; its module layout had already been superseded by [ADR-0006](ADR-0006-architecture-style-and-module-cut.md). The text below is kept as written.

## Context
The brief prefers Java (Spring Boot), Hibernate/JPA and PostgreSQL. It requires:
- a pure decision core (hexagonal);
- test-first development;
- an append-only audit log;
- exact decimal money;
- idempotency.

Constraints at the time:
- **Hackathon:** the product is built for the FinTechers Hackathon (Mon 19 Oct 12:00 to Tue 20 Oct 13:30). A foundation is built before the event; the judged features are built at it. The demo is a 4-minute live demo.
- **Who writes code:** the user and agents only, agents test-first; no teammate writes code (U2, 2026-10-18). The frontend is TypeScript.
- **UI:** the full design comes from Claude Design (React/HTML) and is built in its own epic (the hackathon demo epic, 2026-10-18).
- **Rails:** an x402 crypto rail is in scope; its official SDKs are TypeScript, Python and Go, none for Java.
- **Users:** a single user, no auth.
- **Running it:** one command, locally, on the Pi 5 (aarch64, about 12 GiB free, Docker, no JDK) and on teammates' laptops.
- **LLM:** OpenRouter (about $9.40 in total); AWS Bedrock and AgentCore become available on Day 1.
- **Event APIs:** the event's APIs (TSYS sandbox) are unknown until Day 1.

## Options considered
1. **Java 25 + Spring Boot 4 + PostgreSQL (chosen).**
   - For: the compiler enforces the pure core, because the core is a module with no dependencies. `BigDecimal` is native.
   - Against: a slower edit-run loop than the others on the Pi, and AWS agent tooling that is Python-first. Boot 4 idioms (Jackson 3, modular starters) are newer than most training data.
2. **TypeScript/Node.**
   - For: the fastest loop and the best UI path.
   - Against: there is no decimal type, so invariant 6 rests on a library and lint rules. Module boundaries are enforced only by lint.
3. **Python/FastAPI.**
   - For: a fast loop, a native `Decimal`, and the best fit with AgentCore and Strands.
   - Against: hexagonal boundaries and decimal discipline are kept only by convention.

Within option 1, three smaller choices were made:
- plain SQL (`JdbcClient`) over Hibernate/JPA (U1, user 2026-10-18);
- a React + TypeScript single-page UI over Thymeleaf + htmx (user 2026-10-18), so the Claude Design output is used almost as-is instead of re-written as templates;
- PostgreSQL in Compose over SQLite or H2.

## Decision
- **Language and build:** Java 25 LTS, with Spring Boot 4.1.x used only outside the core. Gradle with the Kotlin DSL and the wrapper.
- **Modules:**
  - `core`: pure Java with no dependencies. Holds the domain, the rules and the ports.
  - `app`: the Spring Boot adapters and the REST + SSE API.
  - `web`: the UI, React + TypeScript with Vite; it talks to the API only.

  ArchUnit bans clock, random and framework use in `core`.
- **Persistence:** PostgreSQL 18 in Docker Compose. Spring `JdbcClient` with SQL, no JPA. Flyway SQL migrations.
  - The audit log is insert-only: the app's database role lacks UPDATE and DELETE, and a trigger rejects them.
  - Idempotency comes from a unique key plus `ON CONFLICT DO NOTHING`.
  - Amounts are `numeric(19,2)`; rates are `numeric(18,8)`.
- **Money:** a `Money` record in `core`. It holds a `BigDecimal` at scale 2 with HALF_EVEN rounding and a single currency. Conversion goes through an `FxRates` port before every comparison.
- **Tests:** JUnit, AssertJ, jqwik (property tests), ArchUnit, Testcontainers with PostgreSQL, and scenario replays from JSON fixtures. Few Spring context tests.
- **LLM:** the ports live in `core`. Spring AI 2.0 adapters: OpenAI-compatible against OpenRouter now, Bedrock Converse at the event. Tests use a fixture adapter. A token budget cap guards the spend.
- **UI:** React + TypeScript (Vite), a single-page app against the `app` API (REST, SSE for live updates). The production build is copied into the app image and served by Spring, so the demo is still one container plus the database. Tests: Vitest; the UI holds no decision logic.
- **x402:** our Java side only decides and issues the single-use approval; the signing wallet side uses the official x402 TypeScript SDK. Where that code lives is decided in the x402 subtask.
- **AWS (U3):** unknown until Day 1. Every LLM and agent integration stays behind a port, so Bedrock or an AgentCore-hosted agent plugs in without changing `core`.
- **Run:**
  - Demo and teammates: `docker compose up --build` starts the app and the database, with multi-arch images.
  - Developer loop: `./gradlew test` and `./gradlew check`; for the UI, `npm run dev` with a proxy to the API, and `npm test`.

## Consequences
- \+ Invariants 6 and 9 are enforced by the type system and the build, not by review alone.
- \+ The demo is still one command and one app container; the designed screens drop in with little rework.
- − Two toolchains (JDK and Node) on the Pi and the laptop; the Docker build is multi-stage so the demo laptop needs only Docker.
- \+ The event's APIs plug in as `RestClient` adapters behind existing ports.
- − The loop on the Pi is slower than with Node or Python. Mitigation: a pure, fast `core` test suite and few Spring tests.
- − This deviates from the brief's Hibernate preference, so mappings are written by hand. That is a small job, since there are few aggregates.
- − Boot 4 and Jackson 3 idioms trip agents up. The skeleton built before the event is the pattern to copy.
- − If the event requires an agent hosted on AgentCore, that agent is a separate (probably Python) process that calls our API. The wallet stack doesn't change.
- Setup and exact versions are pinned in T-008. Checks still to do there:
  - the OpenRouter property and base URL in Spring AI 2.0;
  - that ArchUnit and jqwik work with JUnit 6;
  - how fast a Spring context starts on the Pi.
