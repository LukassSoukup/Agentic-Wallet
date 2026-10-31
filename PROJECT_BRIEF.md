# Project brief: Agentic Wallet

## Goal
Build **Agentic Wallet**: a shopping agent people can trust, because a deterministic control layer stands between the agent and the money. In a familiar chat the user tells the agent, running on the LLM they choose, what to buy and under which rules. The agent searches, recommends and buys through the card networks' agent programmes: Visa Intelligent Commerce and Mastercard Agent Pay. Nothing is paid unless the control layer approves it against the user's confirmed mandate. Every step is logged, so the user and their bank can see what was wanted, what was bought and why.

**The agent proposes, we decide, the issuer executes.** We never hold customer money, never see a card number, never invent a payment protocol and never let a model approve anything.

## Position and channel
- **We are the agent platform and the control layer.** We build the shopping agent ourselves (decided 2026-10-29), because an MCP provider sitting beside someone else's agent cannot own the step-up question or the approved purchase. Other agent hosts can use the same control-layer MCP server later.
- **Delivered to banks first.** The cardholder uses it inside their bank's app under the bank's brand. The bank is the registered party on the networks, and we run under its programme membership and token requestor position. The bank gets the decisions, the reasons, the agent's trace and the cart. Its own authorisation hook is an extra check, and it controls the agent: shadow mode, kill switch, which models are allowed, deployment inside its perimeter.
- **Standalone later,** once we can register with the networks ourselves and the programmes open to consumers. The cardholder app stays separable from any bank shell.
- **Two lines of defence, three where a bank integrates:** our decision before any credential exists; the network's own enforcement of the limits we compile into its instruction; the bank processor's real-time authorisation hook, matched to our pre-approval. An order through a merchant connector (concept 8) has the decision line and, in a bank, the hook on a dedicated agent card; without that card it has one line, and the app says so.

## Core concepts
**1. The agent (our harness).** A chat with a model picker (our gateway's model list; a bank may restrict or replace it), shopping tools, a guided mandate conversation and a memory of past purchases, merchants and habits. The agent and everything it reads are untrusted. It reaches the control layer only through a fixed set of MCP tools, talks to no other MCP server than ours, and never holds a card token, a payment credential or a merchant account.

**2. Mandates.** The user describes what the agent may do in plain words: budget and period, allowed merchants, what to buy and its must-haves, when and how often, expiry. The agent drafts structured rules and shows them back. They apply only after the user confirms that exact version on the confirm screen, which the agent cannot press. Rules can be tightened at any time, never loosened silently. Memory and habits may lead to a question, never to a permission.

**3. Decisions.** For each proposed purchase the control layer answers **Approve**, **Decline** or **Step up** (ask the user in the app), always with reasons. Fixed, testable rules decide. A model or risk signal may only make a decision more cautious.

**4. The cart, and when we can buy without asking.** No card network gives anyone the merchant's cart, and the agent's own account of it is untrusted. So the backend opens the checkout session with the merchant itself and decides on the cart the merchant returns. **A purchase goes ahead without asking only when fixed rules over the merchant's cart settle every requirement in the mandate.** When the cart leaves a requirement open (the merchant sends a title but not the size), the user is shown that cart and asked. A merchant that offers neither an agent checkout session nor an admitted connector (concept 8) cannot be bought from: there is no binding cart or quote to decide on, so the answer is "can't buy here", never a question. A broken limit is a decline either way. Today that means buying only at the few merchants that run protocol checkout sessions, plus the ones in our connector catalogue.

**5. Checkout and payment.** Checkout happens through the networks' agent protocols: Visa's agent checkout gateway (UCP and ACP sessions, with Trusted Agent Protocol signatures where the merchant requires them) and Mastercard Agent Pay Level 3 with Verifiable Intent; or, since 2026-10-31, through an admitted merchant connector, the merchant's own ordering API called by the trusted backend on a binding quote. Never by filling a web form or automating a browser. A merchant outside those paths gets "can't buy here". After an approval, the trusted backend requests one single-use credential for exactly that amount and merchant (or places the order through the user's linked merchant account), fills the buyer's, traveller's or delivery details from the user's own profile (the agent never supplies them), completes the checkout and discards the credential.

**6. Fraud-aware and auditable.** Text from shops and agents is data and never changes rules; hostile text leads at least to a question. Only approved purchases count toward limits. Foreign currencies are converted to the base currency before any comparison. Repeated delivery of the same request is handled idempotently. Every decision, and the whole thread from the user's first prompt to the purchase, is logged append-only, replayable and exportable.

**7. Reconciliation.** What we approved is matched against the authorisation and the clearing. Mismatches open a case with drafted dispute evidence. Money that settled without our approval (network stand-in, over-capture) is recorded and flagged.

**8. Merchant connectors (decided 2026-10-31).** Some merchants publish their own API or MCP server (a grocer with delivery slots and a card on file). Users cannot plug such servers into the agent. We, or the bank for its deployment, admit a merchant into a **connector catalogue** after a security review of a pinned description of its tools; users see the catalogue in the app, turn individual connectors on and off, and link their own merchant account where the connector needs one, by consenting at the merchant from the app. The agent may only search a connected merchant's catalogue; account data and quotes are read by the trusted backend, and an order is placed by it only after an approval, on the quote it decided on, with the user's linked account. Turning a connector on grants no permission to buy; the mandate must name the merchant and the linked account. Where the final amount can differ from the quote (weighed goods, substitutions), the decision approves a ceiling. This comes after v1 (V10).

## Features
Feature IDs are used in issues, PRs and commits. Defined here since 2026-10-29; they keep the numbering of the earlier strategist handoff, which this brief replaces. Ids not listed (V4–V8, N10) were dropped.

| ID | Feature |
|---|---|
| V1 | Chat with the LLM the user picks from the allowed list; spend cap per user |
| V2 | Shopping agent: searches anywhere, buys only where a network agent checkout or an admitted merchant connector exists |
| V3 | Memory of purchases, merchants and habits, as a ranking input only (minimal in v1) |
| M1 | Agent registry: the user's agent instances, status, bound mandates, enrolled cards as token references, stop switch |
| M2 | Mandate capture: words → draft → shown back → active only after confirmation; tighten any time; revoke at once |
| M3 | Decision engine: approve, decline or step up with reasons; fixed rules; a model only adds caution; missing data is never permission |
| M4 | Ledger: only approved purchases count; exact decimals, half-even; idempotent; duplicate and split orders caught |
| M5 | Ask the user: one specific question, one-tap answer, re-check before paying, decline on expiry, never invent the answer |
| M6 | Append-only audit log of decisions with inputs and reasons, plus the conversation and the agent trace; replayable, exportable |
| M7 | Approved payment only: one single-use credential per approval, bound to amount and merchant, never stored |
| M8 | Routing: the card and network chosen by fixed rules before the checkout session, within what the user allowed, recorded and re-validated by the decision with the reason |
| M9 | Three-way match: decision, authorisation, clearing |
| M10 | Mismatch detection: different amount, captured twice, one-off became recurring, refund missing, charge with no decision |
| M11 | Mismatch case with expected against actual and drafted dispute evidence, never filed automatically |
| M12 | Chat for rules: set, tighten, revoke (through the confirm screen) |
| M13 | Chat for questions, answered only from the log, linking to the decisions it cites |
| M14 | Chat safety: chat can never loosen a rule or approve a payment |
| M15 | Activity list with honest states and a detail page per purchase: mandate version, decision, reasons, agent trace, cart, payment state |
| M16 | Benchmark against a plain frontier-model prompt on hostile shop text and reconciliation cases; if the prompt comes within about 5 points, the direction is reconsidered |
| M17 | Data behind adapters: the starting dataset, network and processor simulators, a demo shop, synthetic cases |
| M18 | Intent check: does the cart match what the user meant (wrong size, add-ons, lookalikes, return terms); matches, deviates or uncertain; only ever stricter |
| N15 | Bank operations console: per-cardholder mandates, decisions and traces; shadow mode; kill switch; rates and latency (minimal in v1) |
| V9 | Price watch: a standing mandate that buys when a price drops, without the user present (after v1). It buys on its own or not at all: a step-up cannot wait for a fare that lives minutes, so the mandate defaults to "decline and tell me". Where the merchant has no agent checkout it watches, notifies and links. Personal checkout data (traveller, address) comes from the user's profile, never from the agent |
| V10 | Merchant connectors (after v1): the curated connector catalogue, per-user on/off, account linking from the app, discovery through the connector gateway, quotes and orders by the backend, the amount ceiling for groceries, the agent card in a bank |

Later or nice-to-have, unchanged in meaning: N1 ask over WhatsApp or Telegram · N2 voice · N3 "approve and remember" as a narrower permanent rule · N4 rule templates · N5 instant bank-transfer rail through a licensed partner only · N6 crypto rail (x402) · N7 rail cost comparison · N8 auto-filing chargebacks · N9 subscription tracker · N11 risk score from public fraud datasets (caution only) · N12 agent reputation and anomaly alerts · N13 per-agent budgets, shared wallets · N14 weekly digest · N16 developer portal · N17 passkey or 3-D Secure for asking · N18 Czech and English UI, accessibility · N19 spending insights from the bank's transaction history (derived facts only, never raw lines to a model; caution only in decisions; opt-in).

**Don't build:** holding customer money; our own payment protocol; any AI-made approval or any path where chat loosens a rule; company-agent spend management; form filling or browser automation at checkout; user-installed MCP servers or tools in the agent.

## Architecture
A service-based system: an untrusted **agent harness**, a thin **control MCP server**, a thin **connector gateway** for admitted merchants' catalogue tools, a trusted **control backend** built as a hexagonal modular monolith, and a separate **hook responder** for the bank's real-time authorisation call. One PostgreSQL instance for the control backend and the hook responder, a schema per module and a role per deployable; the harness has its own database; events through a transactional outbox, no broker in v1. Each bank gets its own instance, in our EU cloud or inside the bank's perimeter. Details: [docs/architecture/](docs/architecture/README.md), [ADR-0006](docs/adr/ADR-0006-architecture-style-and-module-cut.md), [ADR-0007](docs/adr/ADR-0007-cart-evidence-and-auto-purchase.md), [ADR-0008](docs/adr/ADR-0008-merchant-connectors-and-the-connector-catalogue.md).

## Proof of concept by 26 December 2026
A working application outside any bank, good enough to lead sales:
- sandbox cards;
- Visa through the live Cybersource sandbox;
- Mastercard through a labelled simulator until we have sandbox access;
- our own demo shop as the merchant;
- the hook responder in shadow mode against a processor simulator;
- the control layer, the audit export and the M16 benchmark;
- the cardholder app as a standalone web app with local accounts.

The date may cut scope, never quality. A signed bank pilot is the goal for 2027.

## Data
- The project will eventually use realistic payment, banking and back-office data from a bank partner. None is available yet.
- **Starting dataset:** a ready-made pack of customers, cards, accounts, merchants, items, FX rates, authorisation history and purchase attempts, at:

  `data/raw/leash-starter/` (copied from `~/IdeaProjects/leash/data/`, hashes in its `metadata.json`)

  Start with `data_dictionary.md` and `schemas/`. Treat this folder as **read-only input data only**. Use nothing else from the `leash` repository: no code, docs, design or architecture.
- If more data is needed, look for **realistic public datasets** (PaySim, IEEE-CIS Fraud Detection, Credit Card Fraud Detection (ULB), Sparkov). Check their licences first. Fill any remaining gaps with our own synthetic data, always labelled.
- All data access goes behind interfaces, so a bank's real feeds are one more adapter.
- Amounts use exact decimal types; the base currency is configurable (CZK for the product); other currencies are converted before comparison.

## Tech stack (decided 2026-10-29; recorded in [ADR-0009](docs/adr/ADR-0009-tech-stack-revision.md))
Java 25, Spring Boot, Spring AI with its MCP server and client, Maven, PostgreSQL with Flyway migrations and Hibernate/JPA in persistence adapters only, Spring Modulith; React and TypeScript for the cardholder app and console; JUnit, Mockito, jqwik, Testcontainers, ArchUnit; SonarQube or SonarCloud, GitHub Issues and GitHub Actions. The decision core is a pure Java module with no framework, database, HTTP, clock or random dependency.

## Trade-offs we accept
Predictability and safety come before convenience. Unclear cases go back to the user more often: every free-text requirement the merchant's cart cannot settle. Merchants without an agent checkout session or an admitted connector are out of reach, and users cannot add their own. The agent can't complete purchases its rules don't cover. Reach grows with the networks' merchant programmes and with the connector catalogue we curate, where a merchant can prepare its own connector against our published format; not with users wiring up servers. A connector order without a bank-issued agent card has one line of defence, and that mode is labelled wherever it shows.

## What to do next
The planning items in [docs/backlog.md](docs/backlog.md), in its order: toolchain (T-008), a walking skeleton that confirms the architecture on the Pi (T-037), domain model and contracts (T-003), payment networks (T-005), the checks in T-034 to T-036 and T-038, data (T-006), milestones (T-007). Don't write production code until the plan is agreed.
