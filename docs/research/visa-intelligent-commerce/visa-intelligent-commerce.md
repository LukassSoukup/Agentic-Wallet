# Visa Intelligent Commerce: knowledge base

What we know about Visa Intelligent Commerce (VIC) as it is offered through Cybersource, its sandbox, and how this project uses it. Start here.

Started 2026-10-23 in TASK-051. The rail itself is TASK-034. The network comparison stays in [payment-networks](../payment-networks.md).

## Pages
| Page | What it holds |
|---|---|
| [vic-sources](vic-sources.md) | Where the facts come from: the Cybersource developer MCP, the guide, the API reference, the SDK repos. How to query each. |
| [vic-api-surface](vic-api-surface.md) | The `/icc/v1` endpoints, call order, message-level encryption (MLE), auth, the request models and how they map to our domain. |
| [vic-sandbox-access](vic-sandbox-access.md) | How to get a sandbox account and which credentials we need, and where they live. |
| [vic-open-questions](vic-open-questions.md) | Contradictions between sources and unknowns. Each becomes a `needs_user:` or an analysis item on the task that hits it. |
| [vic-sandbox-log](vic-sandbox-log.md) | Dated record of every real call we make against the sandbox and what came back. Grows with the project. |

## How to maintain it
- A fact goes in with its source and date. Unsourced guesses don't go in; they go to [vic-open-questions](vic-open-questions.md).
- When a live sandbox call confirms or contradicts a page, add an entry to [vic-sandbox-log](vic-sandbox-log.md) and fix the page it touches.
- Secrets never go here. Not keys, not IDs that identify our account. Only the names of the settings that hold them.
- The docs move fast (the SDK was v0.0.95 on 2026-10-23). Re-check a fact against the MCP before relying on it in code.

## Status (2026-10-24)
- Documentation: full core-flow models read (SDK 0.0.95) and the whole developer guide; the guide and the SDK contradict each other on several shapes ([vic-api-surface](vic-api-surface.md)).
- Sandbox account: not yet. Signup and Business Center keys are self-serve; network tokens, the TRID and relationship ID, and probably Intelligent Commerce itself need a request to Cybersource support ([vic-sandbox-access](vic-sandbox-access.md) step 3).
- Live calls: one unauthenticated route probe ([vic-sandbox-log](vic-sandbox-log.md)).
