---
id: ADR-0008
status: proposed
date: 2026-10-18
tasks: ["EPIC-021"]
---
# ADR-0008: Payment methods, single-use authorisation and rails

> **Re-opened 2026-10-23 by TASK-048:** the rails and the hand-off were shaped by the event. To be superseded by the ADR of TASK-007 (hand-off and validity from the public specs, simulators first, x402 deferred).

## Context
Handoff M7 and M8; the user added payment methods with blocking and a crypto rail (2026-10-18, EPIC-021). The handoff forbids holding customer money and saying we initiate payments. Network facts in [payment-networks](../research/payment-networks.md).

## Options considered
1. **We decide and authorise; the issuer or the user's wallet executes (chosen).**
2. We hold a wallet key and pay: custody, contradicts the handoff.
3. Card rails only: leaves out the user's crypto request.

## Decision
- **Payment methods:** the user's cards (network, last 4, token reference) and crypto wallets (chain, address, asset); ordered by preference; blockable at once. No PAN, CVV or private key is stored.
- **Routing** happens inside the decision: user's methods ∩ mandate's allowed methods ∩ agent networks, minus blocked ones, filtered by rail conditions; first by preference; no route → DECLINE.
- **Authorisation:** single-use, bound to decision, amount, currency, merchant, method, expiry. A `PaymentRail` port executes it. How it reaches whoever executes, and its validity, follow the Day-1 sandbox answer.
- **Rails:** Visa Intelligent Commerce against the live self-serve sandbox; Mastercard Agent Pay simulated with documented shapes and labelled simulated; x402 on testnet only, signed by the user's wallet side (TypeScript, official SDK) after our authorisation, and checked against it.
- **Wording:** the agent proposes, we decide, the issuer or wallet executes.

## Consequences
- \+ One contract suite covers every rail; TSYS or more networks are one more adapter.
- − The Visa rail needs the user's sandbox keys; the x402 rail needs a testnet wallet.
- − Mastercard stays simulated until registration.
