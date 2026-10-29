# Glossary (ubiquitous language)

Draft from [PROJECT_BRIEF.md](../../PROJECT_BRIEF.md). It gets refined in T-003.

- **Agent**: a registered AI shopping agent with an identity, a status and a link to a payment network (Mastercard, Visa or both). It searches, builds orders and proposes purchases. It can't pay without a Decision.
- **Payment network**: Mastercard and/or Visa agentic payment rails.
- **Mandate**: the user's rules for what agents may do. Written in plain language, compiled into structured rules, shown back and active only after confirmation. Can be tightened at any time, never loosened silently.
- **Mandate compiler**: an LLM-guided conversation that helps the user state their rules, asks clarifying questions, and compiles the result into structured hard rules (the mandate draft). It's an authoring aid only. It never takes part in a decision, and its output has no effect until the user confirms it.
- **Mandate draft**: a compiled but not yet confirmed mandate. It has no effect.
- **Purchase proposal / attempt**: an order an agent asks the control layer to approve.
- **Control layer**: the deterministic decision engine plus security checks between agents and payment.
- **Decision**: `APPROVE`, `DECLINE` or `STEP_UP`, always with reasons. Logged append-only.
- **Step-up**: a question to the user in the app for unclear cases (missing details, possible duplicate, split order). It doesn't count toward limits until approved.
- **Risk signal**: an optional model output that may make a Decision more cautious, never less.
- **Untrusted text**: any text from merchants or agents (descriptions, item details). It is data, never instructions.
- **Base currency**: the currency limits are compared in. Configurable (brief: CZK; the leash test pack: CHF, see [starting-dataset](../data/starting-dataset.md)).
- **Routing**: choosing, inside the decision, which of the user's allowed and unblocked payment methods pays, with the reason. No route means DECLINE.
- **Reconciliation**: matching captured charges (later also settlement) against the decision and authorisation behind them.
- **Payment method**: a card (network, last 4, token reference) or crypto wallet (chain, address, asset) the user added; ordered by preference; can be blocked at once.
- **Rail**: the way a payment method is executed: Visa Intelligent Commerce, Mastercard Agent Pay, x402.
- **Global budget**: one budget all agents draw from, with its own period; a purchase may not exceed the smaller of it and the agent's own remaining limit.
- **Intent check**: compares the user's rules with the proposed purchase, reading untrusted text; verdict matches, deviates or uncertain; it can only make a decision stricter.
- **Suspicious-text flag**: set when untrusted text contains instructions; raises the decision to at least STEP_UP.
- **Payment authorisation**: single-use permission bound to one approved decision, amount, merchant and method.
- **Capture**: the charge as the issuer reports it, read from capture files.
- **Mismatch case**: opened when a capture differs from what was approved; holds expected against actual and a drafted evidence package, never filed automatically.
- **Audit log**: append-only record of every Decision with its inputs and reasons, which can be replayed.
- **Idempotency key**: identifies repeat deliveries of the same request, so they yield the same Decision once.
