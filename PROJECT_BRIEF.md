# Project brief: Agentic Wallet

## Goal
Build **Agentic Wallet**: a shopping agent people can trust, because a deterministic control layer stands between the agent and the money. In a familiar chat the user tells the agent, running on the LLM they choose, what to buy and under which rules. The agent searches, recommends and buys at shops that offer an agent checkout, paying with the card or wallet the user already has, through a payment provider (Stripe first). Nothing is paid unless the control layer approves it against the user's confirmed mandate. Every step is logged, so the user can see what was wanted, what was bought and why.

**The agent proposes, we decide, the shop charges through the provider, the user's bank pays.** We never hold a payment licence, never hold customer money, never see a card number, never invent a payment protocol and never let a model approve anything.

## Position and channel
- **We are the agent platform and the control layer.** We build the shopping agent ourselves (decided 2026-10-29), because an MCP provider sitting beside someone else's agent cannot own the step-up question or the approved purchase. Other agent hosts can use the same control-layer MCP server later.
- **A standalone consumer app first (decided 2026-11-19).** Users anywhere in the EU sign up with us directly, save the card or wallet they already have through the payment provider's own form, and use the app under our brand. Each user's currency is set from their location at sign-up (EUR by default) and can be changed. We run one installation in an EU cloud and are the data controller.
- **Banks later.** A bank may later offer the app to its customers. Nothing bank-specific is built now; the door stays open because payment sits behind a port and every decision already produces the exact limits a bank-side check could enforce.
- **Two lines of defence, honestly named:** our decision before any payment credential exists; and the provider's enforcement of the token we issue for that decision (one shop, at most the approved amount, a short expiry, revocable). The second line's limits are set by us, so the app says what it is ("this shop can charge at most €90, until 10:30"), never that it is independent. An order through a merchant connector (concept 8) paid with the card on file at the merchant has one line, and the app says so.

## Core concepts
**1. The agent (our harness).** A chat with a model picker (our gateway's model list), shopping tools, a guided mandate conversation and a memory of past purchases, merchants and habits. The agent and everything it reads are untrusted. It reaches the control layer only through a fixed set of MCP tools, talks to no other MCP server than ours, and never holds a payment token, a payment credential or a merchant account.

**2. Mandates.** The user describes what the agent may do in plain words: budget and period, allowed merchants, what to buy and its must-haves, when and how often, expiry. The agent drafts structured rules and shows them back. They apply only after the user confirms that exact version on the confirm screen, which the agent cannot press. Rules can be tightened at any time, never loosened silently. Memory and habits may lead to a question, never to a permission. A mandate is never restricted to one currency.

**3. Decisions.** For each proposed purchase the control layer answers **Approve**, **Decline** or **Step up** (ask the user in the app), always with reasons. Fixed, testable rules decide. A model or risk signal may only make a decision more cautious.

**4. The cart, and when we can buy without asking.** The agent's own account of a cart is untrusted. So the backend opens the checkout session with the shop itself and decides on the cart the shop returns (signed by the shop where the protocol provides it). **A purchase goes ahead without asking only when fixed rules over the shop's cart settle every requirement in the mandate.** When the cart leaves a requirement open (the shop sends a title but not the size), the user is shown that cart and asked. The first purchase at a shop where the user never bought through us never goes ahead without the user: it is a question, or, on a purchase while the user is away, a decline that tells them. A shop that offers neither an agent checkout session nor an admitted connector (concept 8) cannot be bought from: there is no binding cart or quote to decide on, so the answer is "can't buy here", never a question. A broken limit is a decline either way. The user always sees the shop's own price and the currency their card will be charged in; our rules convert only to check the price against a limit, into that limit's currency.

**5. Checkout and payment.** Checkout happens through the shops' agent checkout protocols (Google's UCP, the ACP of OpenAI and Stripe) at the shop's own endpoint, found from the shop's own published discovery document, never from the agent; or, since 2026-10-31, through an admitted merchant connector on a binding quote. Never by filling a web form or automating a browser. After an approval, the trusted backend issues **one payment token through the provider for exactly that shop, amount, currency and a short expiry**, drawn on the user's saved card or wallet (Stripe Shared Payment Tokens first; later the card networks' agent tokens through a partner, and Google Pay once Google opens it), fills the buyer's or delivery details from the user's own profile (the agent never supplies them), completes the checkout and switches the token off after its use. When the user's bank asks for a 3-D Secure confirmation, the user gives it in our app; when the user is away and it lapses, the purchase fails cleanly and the user is told. Revoking a mandate also revokes a token the shop has not used yet. The provider key our backend holds can save cards and issue tokens, never charge or refund, so money goes from the user's bank to the shop and never through us.

**6. Fraud-aware and auditable.** Text from shops, reviews and agents is data and never changes rules; hostile text leads at least to a question. Only approved purchases count toward limits. Amounts in other currencies are converted into the limit's currency before any comparison. Repeated delivery of the same request is handled idempotently. Every decision, and the whole thread from the user's first prompt to the purchase, is logged append-only, replayable and exportable.

**7. Reconciliation.** What we approved is matched against the provider's record of the token's use and the shop's order record. Mismatches open a case with drafted dispute evidence the user files with their bank. What we cannot see (the amount billed in the card's currency, chargebacks, charges outside our token, refunds the shop does not report) is said so in the app.

**8. Merchant connectors (decided 2026-10-31).** Some merchants publish their own API or MCP server (a grocer with delivery slots and a card on file). Users cannot plug such servers into the agent. We admit a merchant into a **connector catalogue** after a security review of a pinned description of its tools; users see the catalogue in the app, turn individual connectors on and off, and link their own merchant account where the connector needs one, by consenting at the merchant from the app. The agent may only search a connected merchant's catalogue; account data and quotes are read by the trusted backend, and an order is placed by it only after an approval, on the quote it decided on, with the user's linked account. Turning a connector on grants no permission to buy; the mandate must name the merchant and the linked account. Where the final amount can differ from the quote (weighed goods, substitutions), the decision approves a ceiling. This comes after v1 (V10).

**9. Merchant scan (decided 2026-11-19).** When a search turns up a shop the user never bought from through us, the agent also looks up the shop's reviews and rating, ranks results with it and tells the user. We keep a record of shops users bought from successfully. A rating may order results and add caution, never permit a purchase.

## Features
Feature IDs are used in issues, PRs and commits. Defined here since 2026-10-29; they keep the numbering of the earlier strategist handoff, which this brief replaces. Ids not listed (V4–V8, N10) were dropped.

| ID | Feature |
|---|---|
| V1 | Chat with the LLM the user picks from the allowed list; spend cap per user |
| V2 | Shopping agent: searches anywhere, buys only where a shop's agent checkout accepting our provider's tokens or an admitted merchant connector exists |
| V3 | Memory of purchases, merchants and habits, as a ranking input only (minimal in v1) |
| V11 | Merchant scan: reviews and rating of shops new to the user, as ranking and caution only, shown to the user |
| M1 | Agent registry: the user's agent instances, status, bound mandates, stop switch; the user's saved payment methods as provider references |
| M2 | Mandate capture: words → draft → shown back → active only after confirmation; tighten any time; revoke at once |
| M3 | Decision engine: approve, decline or step up with reasons; fixed rules; a model only adds caution; missing data is never permission |
| M4 | Ledger: only approved purchases count; exact decimals, half-even; idempotent; duplicate and split orders caught |
| M5 | Ask the user: one specific question, one-tap answer, re-check before paying, decline on expiry, never invent the answer |
| M6 | Append-only audit log of decisions with inputs and reasons, plus the conversation and the agent trace; replayable, exportable |
| M7 | Approved payment only: one provider token per approval, bound to shop, amount, currency and expiry, revoked or switched off after use; our provider key cannot charge |
| M8 | Routing: which saved payment method pays, chosen by fixed rules within what the user allowed, recorded and re-validated by the decision with the reason |
| M9 | Match: decision, the provider's token events, the shop's order record |
| M10 | Mismatch detection where we can see it: different amount, used twice, an order with no decision, a missing confirmation |
| M11 | Mismatch case with expected against actual and drafted dispute evidence, never filed automatically |
| M12 | Chat for rules: set, tighten, revoke (through the confirm screen) |
| M13 | Chat for questions, answered only from the log, linking to the decisions it cites |
| M14 | Chat safety: chat can never loosen a rule or approve a payment |
| M15 | Activity list with honest states and a detail page per purchase: mandate version, decision, reasons, agent trace, cart, payment state |
| M16 | Benchmark against a plain frontier-model prompt on hostile shop text and reconciliation cases; if the prompt comes within about 5 points, the direction is reconsidered |
| M17 | Data behind adapters: the starting dataset, a payment provider simulator, a demo shop, synthetic cases |
| M18 | Intent check: does the cart match what the user meant (wrong size, add-ons, lookalikes, return terms); matches, deviates or uncertain; only ever stricter |
| N15 | Operator console: admitted connectors, kill switch, rates and latency (minimal in v1) |
| N17 | 3-D Secure confirmations in the app through the provider's own window (needed in v1); passkeys later |
| V9 | Price watch: a standing mandate that buys when a price drops, without the user present (after v1). It buys on its own or not at all: a step-up cannot wait for a fare that lives minutes, so the mandate defaults to "decline and tell me"; a 3-D Secure request while the user is away lets the purchase lapse and tells the user. Where the merchant has no agent checkout it watches, notifies and links. Personal checkout data (traveller, address) comes from the user's profile, never from the agent |
| V10 | Merchant connectors (after v1): the curated connector catalogue, per-user on/off, account linking from the app, discovery through the connector gateway, quotes and orders by the backend, the amount ceiling for groceries |

Later or nice-to-have, unchanged in meaning except N18 (languages, 2026-11-19) and the new N20: N1 ask over WhatsApp or Telegram · N2 voice · N3 "approve and remember" as a narrower permanent rule · N4 rule templates · N5 instant bank-transfer rail through a licensed partner only · N6 crypto rail (x402) · N7 rail cost comparison · N8 auto-filing chargebacks · N9 subscription tracker · N11 risk score from public fraud datasets (caution only) · N12 agent reputation and anomaly alerts · N13 per-agent budgets, shared wallets · N14 weekly digest · N16 developer portal · N18 English first, then Czech and other EU languages, accessibility · N19 spending insights from the user's own bank data (derived facts only, never raw lines to a model; caution only in decisions; opt-in) · N20 the bank channel: the app under a bank's brand, with the bank's own authorisation check as a third line.

**Don't build:** a payment licence of our own or holding customer money; a provider key that can charge; our own payment protocol; one-time virtual cards that need the user to open or fund an account at an issuer; any AI-made approval or any path where chat loosens a rule; company-agent spend management; form filling or browser automation at checkout; user-installed MCP servers or tools in the agent.

## Architecture
A service-based system: an untrusted **agent harness**, a thin **control MCP server**, a thin **connector gateway** for admitted merchants' catalogue tools, and a trusted **control backend** built as a hexagonal modular monolith that also receives the payment provider's signed webhooks. One PostgreSQL instance for the control backend, a schema per module and a role per deployable; the harness has its own database; events through a transactional outbox, no broker in v1. One multi-tenant installation in an EU cloud. Details: [docs/architecture/](docs/architecture/README.md), [ADR-0006](docs/adr/ADR-0006-architecture-style-and-module-cut.md), [ADR-0007](docs/adr/ADR-0007-cart-evidence-and-auto-purchase.md), [ADR-0008](docs/adr/ADR-0008-merchant-connectors-and-the-connector-catalogue.md), [ADR-0010](docs/adr/ADR-0010-standalone-app-and-provider-payment-tokens.md).

## Proof of concept by 26 December 2026
A working standalone application:
- Stripe in test mode: saved test cards and wallets, Shared Payment Tokens, 3-D Secure test cards, the token webhooks;
- our own demo shop as the merchant, with a UCP-style checkout session, accepting the tokens as a Stripe test seller, labelled synthetic;
- the control layer, the audit export and the M16 benchmark;
- the cardholder app as a web app with local accounts.

The date may cut scope, never quality. Live payments wait for a legal opinion on our position under the EU payment rules and on our duties as data controller.

## Data
- No real payment data is available. All data access goes behind interfaces, so real feeds are one more adapter.
- **Starting dataset:** a ready-made pack of customers, cards, accounts, merchants, items, FX rates, authorisation history and purchase attempts, at:

  `data/raw/leash-starter/` (copied from `~/IdeaProjects/leash/data/`, hashes in its `metadata.json`)

  Start with `data_dictionary.md` and `schemas/`. Treat this folder as **read-only input data only**. Use nothing else from the `leash` repository: no code, docs, design or architecture. Its bank-shaped parts (authorisation history) serve tests and the benchmark, not a bank integration.
- If more data is needed, look for **realistic public datasets** (PaySim, IEEE-CIS Fraud Detection, Credit Card Fraud Detection (ULB), Sparkov). Check their licences first. Fill any remaining gaps with our own synthetic data, always labelled.
- Amounts use exact decimal types; each user has a base currency (from their location, EUR by default); other currencies are converted before comparison.

## Tech stack (decided 2026-10-29; recorded in [ADR-0009](docs/adr/ADR-0009-tech-stack-revision.md))
Java 25, Spring Boot, Spring AI with its MCP server and client, Maven, PostgreSQL with Flyway migrations and Hibernate/JPA in persistence adapters only, Spring Modulith; React and TypeScript for the cardholder app and console; JUnit, Mockito, jetCheck (jqwik until 2026-11-01, ADR-0009 option 13), Testcontainers, ArchUnit; SonarQube or SonarCloud, GitHub Issues and GitHub Actions. The decision core is a pure Java module with no framework, database, HTTP, clock or random dependency.

## Trade-offs we accept
Predictability and safety come before convenience. Unclear cases go back to the user more often: every free-text requirement the shop's cart cannot settle, and every first purchase at a shop new to the user. Shops without an agent checkout that accepts our provider's tokens, or an admitted connector, are out of reach, and users cannot add their own; today that is a small slice of EU shops. The agent can't complete purchases its rules don't cover. The second line of defence is the provider's enforcement of limits we set, not a bank's independent check; banks may add one later. We cannot see what the user's bank finally bills in another currency, refunds or chargebacks. A 3-D Secure request can make an unattended purchase lapse. A connector order paid with the card on file at the merchant has one line of defence, and that mode is labelled wherever it shows.

## What to do next
The planning items in [docs/backlog.md](docs/backlog.md), in its order: domain model and contracts (T-003 to T-041), the payment provider (T-005), the agent checkout check (T-034), merchant scan (T-046), consumer sign-up (T-044), the connector and intent model checks (T-038, T-036), data (T-006), milestones (T-007); before live payments, the legal opinion (T-043) and retention and erasure (T-045). Done: toolchain (T-008), the walking skeleton (T-037), the standalone reorientation (T-042). Don't write production code until the plan is agreed.
