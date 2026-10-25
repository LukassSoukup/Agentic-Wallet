# Project brief: Agentic Wallet

## Goal
Build **Agentic Wallet**: a payment wallet for AI shopping agents. Registered AI agents, connected to the Mastercard and/or Visa agentic payment networks, find and buy products on the user's behalf. They also start, route and reconcile the payments that follow.

Every purchase an agent proposes must first pass through a **control and security layer**. That layer checks the order against the user's rules and decides whether it goes ahead. The product is "AI agents that initiate, route and reconcile payments, plus fraud-aware agentic checkout".

## Core concepts

**1. Agents**
- A registry of AI shopping agents, each with an identity, a status and a link to a payment network (Mastercard, Visa or both).
- Agents search, build orders and propose purchases. They can't pay without a decision from the control layer.
- After an approved purchase, the agent's payment is routed and later reconciled against settlement data.

**2. User rules (mandates)**
- The user describes what agents may do in plain words, for example "at most 5 000 CZK a week, only shops I've bought from before, delivery only, one item per order".
- The system turns this into structured rules, shows them back and applies them only after the user confirms.
- Rules can be made stricter at any time but are never loosened quietly. Preferences and past behaviour may lead to a question, never to a permission.

**3. Decisions**
For each proposed purchase the control layer returns one of three answers, each with a short reason:
- **Approve:** the order fits all rules.
- **Decline:** it breaks a rule, such as a spending limit or an unfamiliar shop.
- **Step up:** the case is unclear (missing details, a possible duplicate or a split order), so the user confirms in the app.

**4. Fraud-aware checkout**
- Decisions come from fixed, testable rules. An AI model may flag a risk and make a decision more cautious, but it can never turn a decline or a question into an approval.
- Text from shops and agents is untrusted, so it can't change rules. This defends against hidden instructions in product descriptions.
- Only approved purchases count toward limits. Foreign currencies are converted to CZK before comparison. Repeat deliveries of the same request are handled idempotently.
- Every decision is logged with its reasons, so it can be audited and replayed.

## Direction and features
The strategist's handoff is the source of truth for **direction and features**, and wins over this brief where they differ (decided 2026-10-17). It lives in `~/IdeaProjects/leash-analysis/wallet_control_handoff/`: read it there, never copy it into this repo, and don't edit it (the strategist maintains it). Feature IDs (`M1`…, `N1`…) come from it; use them in issues, PRs and commits. The domain invariants and the quality bar in [CLAUDE.md](CLAUDE.md) still bind every change, and its ≈26 Dec 2026 decision gate may cut scope, never quality.

Files as read on 2026-10-25 (a changed hash means the handoff moved on; re-read it):

| File | SHA-256 |
|---|---|
| `README.md` | `2d4330b3d06ffe378ae9c5957a86e808efeca445793f03b40f6d641067ea172c` |
| `01-direction.md` | `c058a855f96c679c821103550ce054a6c33353903435fc298f8e2edd339ac633` |
| `02-features.md` | `12b576d6905eb7d6f64fbac8f933d8d220c611f4bde7b47ce3ea671dd1a2816f` |
| `03-constraints-and-open-questions.md` | `e3a9c458acb56faa44393729fa2f8fa5507a0d717e39b5f70ee696bc7e64cd81` |
| `design/README.md` | `9ece2e56d2002c8fc2c92226794e801cbfc00c5ed8725dfbbc49c4cfe0a6e833` |
| `design/01-users-and-surfaces.md` | `5e925e9c036e4ec41724839fb5f3efaed003311ac94117f0d3126b54f4200094` |
| `design/02-principles-and-visual-system.md` | `ff8f7325cd942e99498d7af6cb7980613c938d941a1d8ddaf270b5e2ca099337` |
| `design/03-journeys.md` | `74767e701ea7dea4e6f15f2267eb0168b6c7477de9c24447a580ce65cadb1e2d` |
| `design/04-screens-and-states.md` | `43c4a83803e2b04877468fcd7644f8f5ae836be79ea6e0b6beef7afad6e81a44` |
| `design/05-words-and-content.md` | `0c72e491d262bb7c43bdcfd46a383be287e5c9e7076e03839db2870b66500786` |
| `design/06-data-per-screen.md` | `0036cc8c55c7abe9afd73d70edd8625d749a8cb7fbd7347424d688a91710e57d` |
| `design/07-claude-design-prompts.md` | `1911464ecf16e6e4a6895f313e06d0747dfe90b68a634ba6a3ce54af611da0f7` |
| `design/08-open-design-questions.md` | `e3a7f9ff666bba7514347e9920ce5d8b208d28d81688b08533d3cd196e960b63` |

## Data
- The project will eventually use realistic payment, banking and back-office datasets, including a **TSYS sandbox**. These aren't available yet.
- **Starting dataset:** a ready-made pack of customers, cards, accounts, merchants, items, FX rates, authorization history and purchase attempts is at:

  `data/raw/leash-starter/` (copied from `~/IdeaProjects/leash/data/`, hashes in its `metadata.json`)

  Start with `data_dictionary.md` and `schemas/`. Treat this folder as **read-only input data only**. Use nothing else from the `leash` repository: no code, docs, design or architecture. Design this project independently.
- If more data is needed, look for **realistic public datasets**, for example:
  - PaySim (mobile money transactions)
  - IEEE-CIS Fraud Detection
  - Credit Card Fraud Detection (ULB)
  - Sparkov-generated card transaction datasets

  Check their licences and availability first. Fill any remaining gaps with our own synthetic data.
- Put all data access behind interfaces, so switching to the TSYS sandbox later only means adding a new adapter.
- Amounts use exact decimal types, with CZK as the base currency. Data in other currencies is converted.

## Tech stack (open for debate)
- Preferred: **Java** (Spring Boot), **Hibernate/JPA**, **PostgreSQL**.
- Other choices are welcome if they fit better. Give a short recommendation with trade-offs before committing.
- Requirements: a clean separation between pure decision logic and infrastructure (hexagonal or ports-and-adapters), test-first development, and a database schema that supports an append-only audit log.

## Trade-offs we accept
Predictability and safety come before convenience. Unclear cases go back to the user more often, and agents can't complete purchases their rules don't cover.

## What to do first
1. Ask any clarifying questions that would change the design.
2. Propose the tech stack, with reasoning.
3. Propose a high-level architecture and domain model: agents, mandates, purchases, decisions, payments and reconciliation.
4. Propose a data plan: how to use the starting dataset, which public datasets to add and what to generate synthetically.
5. Propose a milestone plan, starting with a small end-to-end slice: one agent, one mandate and one purchase decision.

Don't write production code until the plan is agreed.
