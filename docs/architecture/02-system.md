# 02 System: deployables, trust zones, communication, deployment

Decided in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md).

## The picture
```
 UNTRUSTED ZONE                      BOUNDARY                 TRUSTED ZONE
┌──────────────────────────┐       ┌──────────────┐        ┌──────────────────────────────────────────┐
│ agent-harness            │  MCP  │ control-mcp  │  REST  │ control-backend (modular monolith)       │
│ chat · model picker      │──────▶│ typed tools  │───────▶│ identity · profiles · settings · agents ·│
│ shopping tools · memory  │◀──────│ trace ingest │◀───────│ mandates · decisioning · ledger ·        │
│ mandate drafting · trace │       │ run claim    │        │ payments · authorisation · reconciliation│
│ watch                    │       │ no database  │        │ · audit · activity · notifications       │
└───┬───────────┬──────────┘       └──────────────┘        └───┬──────────┬──────────────┬────────────┘
    │ LLM       │ search, read pages                           │ networks │ merchants    │ intent model
    ▼ gateway   ▼ (untrusted text)                             ▼ VIC, MC  ▼ checkout     ▼ (extraction)
                                                                            sessions
┌──────────────────────────┐                               ┌──────────────────────────────────────────┐
│ web (React SPA)          │── chat ──▶ agent-harness      │ hook-responder                           │
│ cardholder app · console │── confirm, step-up, activity, │ answers the bank processor's real-time   │
└──────────────────────────┘   stop switch ──▶ control-backend   authorisation call in ≤300 ms p99     │
                                                           └───────────▲──────────────────────────────┘
                                                                       │ bank processor (Thredd, Enfuce, own host)
 PostgreSQL: one schema + role per deployable; hook-responder reads published views of the backend
 TEST ONLY: VIC and Agent Pay simulators · processor hook simulator · clearing file generator · demo shop
```

## Deployables
| Deployable | Zone | Holds | Talks to | Never |
|---|---|---|---|---|
| **agent-harness** | untrusted | chat and streaming, model picker, `ModelGateway` (our gateway list or the bank's), shopping tools (search, read page), mandate drafting conversation, memory (purchases, merchants, habits as structured facts), the watch runner for standing mandates, trace emitter, MCP client | LLM gateway; the open web; `control-mcp` | holds a network credential, a card token, a signing key for checkout, personal checkout data, or any database grant on control data |
| **control-mcp** | boundary | the MCP tool surface; input validation and size caps; typing every free-text field as untrusted; forwarding the agent session or run token; the trace and conversation ingest endpoint; the run claim endpoint | `control-backend` over REST | has a database or other state (quotas and rate limits are enforced in the backend), holds business logic, exposes a tool that confirms, approves or answers a step-up |
| **control-backend** | trusted | the modules in [03-modules.md](03-modules.md); the pure decision core; network and merchant adapters; the audit log | PostgreSQL; Cybersource (VIC); Mastercard (simulated until access); merchants' agent checkout and catalogue endpoints, **only at addresses taken from the networks' merchant registries, never from the agent**; the intent extraction model; FX source | calls a model inside the pure core; lets chat text reach a rule as an instruction; fetches an address the agent supplied |
| **hook-responder** | trusted | processor profiles (Thredd-shaped, Enfuce-shaped, later the bank's own); the pure pre-approval match rule (shared with the backend); shadow or enforcing mode; deduplication by the processor's authorisation id; pre-approval consumption; its call log | PostgreSQL (read-only on published views of the backend, write on its own `hook` schema) | calls the backend, the harness or any model on the request path |
| **web** | the user's device | the cardholder app (chat, confirm screen, step-up answer, profile, notification channel, activity, stop switch) and the minimal bank console; React and TypeScript | `agent-harness` for chat; `control-backend` for everything that changes rules, personal data or answers a question | contains decision logic |
| **simulators, drivers and demo shop** | test only | VIC and Agent Pay simulators, processor hook simulator, clearing file generator, demo shop with a UCP-style checkout session, a scripted agent driver that proposes labelled cases without an LLM, and the M16 benchmark runner (our `decide()` against a plain prompt on the versioned case set); all labelled synthetic | whatever they stand in for | ship in a production image |

## Why the MCP server is separate and thin
The LLM side reaches the control layer only through MCP tools. Making that server its own process with no database turns "the LLM cannot touch the core except through these tools" from a code convention into a network fact. Any later agent host (a bank's own assistant, a partner platform) plugs into the same server. The server is stateless and cheap.

### The tool surface
| Tool | What it does | Notes |
|---|---|---|
| `list_mandates`, `get_mandate` | read the active mandates of the caller | read only |
| `draft_mandate` | turn the conversation's structured draft into a mandate draft and return its plain-words rendering | a draft has no effect (invariant 4) |
| `propose_purchase` | merchant reference, lines (each a merchant item id **or** a typed item spec, with a quantity), `purchaseKey`, the agent's own description and reasons | the agent's text is untrusted and can only add caution ([ADR-0007](../adr/ADR-0007-cart-evidence-and-auto-purchase.md)); a line never carries personal data, that comes from `profiles` |
| `get_decision` | outcome, reasons, payment state of a proposal | read only |
| `get_activity` | answers "what did my agent buy and why" from the log | read only; amounts and ids come from the backend, never from the model |
| `get_agent_configuration` | the allowed models, the LLM spend cap and what is left of it, the agent's status | read only; how the bank's `settings` reach the harness |

The harness learns the outcome of a step-up by polling `get_decision` for its `purchaseKey` while the conversation waits; the user sees the question and the answer in the app in any case. A step-up's expiry is set per decision as `min(the setting in settings, the stored checkout session expiry − a margin)` and recorded in the `DecisionInput`, so an approval after a step-up never lands on an expired session. A merchant whose sessions live minutes (airlines) therefore gets no useful step-up: there the outcome is auto-buy or no buy.

Not tools, by design: confirm a mandate, answer a step-up, approve anything, start a checkout, change an agent's status, revoke a mandate, edit the profile. Confirming, answering, revoking and profile edits come only from the cardholder app to the backend, authenticated as the user. The confirm screen renders the mandate from the backend's **structured draft**, never from the chat's text, so what the user confirms is what the rules will enforce. Checkout starts in the backend after an APPROVE, never because the agent asked.

The agent harness's own runtime (not the model) sends the conversation and the agent trace to `control-mcp`'s ingest endpoint, so the audit export holds the whole thread. Ingest is idempotent by event id and stores the content as untrusted data. Because it comes from the untrusted side it is bounded: a size cap per event, a quota per agent instance and day (configurable, with a separate quota for watch runs so a long watch cannot starve the chat), and an event is attached to a `purchaseKey` only if that key belongs to the token's agent instance; otherwise it is stored unattached. Exceeding a cap is a named error and is itself recorded.

The same runtime claims due runs of standing mandates through `control-mcp`'s **run claim** endpoint (not a tool; the model never sees it). The backend answers with the run's mandate id and a short-lived agent run token, or with "nothing due".

## Identity between deployables
- **User → web → harness / backend:** local accounts in the PoC, the bank's identity provider over OIDC later, behind one `CallerIdentity` port. The backend never holds passwords from a bank.
- **Harness → control-mcp → backend:** the backend's `identity` module issues an **agent session token**: signed by the backend, bound to the cardholder, the agent instance and the audience `control-backend`. For a chat it is issued when the user opens the chat in the web app and is short-lived. The web app hands it to the harness with the chat session; the harness attaches the token to every MCP call and cannot mint or widen one. `control-mcp` forwards it **verbatim** and never derives or adds claims; the backend verifies it itself on every call. The model never supplies or sees identity. An agent's identity comes from this token, never from a request body. `control-mcp` and the backend also authenticate each other with mutual TLS, which proves the caller is `control-mcp` but grants no user rights by itself.
- **Standing mandates** ("look every morning", the price watch V9): the schedule is trusted data. The confirmed mandate carries the run frequency; `agents` keeps the **due runs**. The harness authenticates to `control-mcp` with mutual TLS (one client certificate per harness deployment); `control-mcp` passes that verified identity to the backend under its own mutual TLS. The harness runtime holds one long-lived **agent-instance credential**, bound to that certificate and able to do exactly one thing: claim due runs of its own agent instance. On a claim, `identity` mints a short-lived **agent run token** for that run, bound to the cardholder, the agent instance, the mandate and the run id, and limited to `propose_purchase`, `get_decision`, `get_mandate` and the trace ingest, so an unattended run leaves the same thread in the audit export as a chat. A claim is single-use; a second claim of the same run is refused and recorded, so a stolen credential racing the harness becomes visible. No run is due once the mandate is fulfilled, expired, revoked or the agent stopped, or while the cardholder has no notification channel left; nothing long-lived in the harness can propose a purchase on its own.
- **Bank processor → hook-responder:** the processor's own scheme (shared secret signature or mutual TLS), per profile.

## Data ownership in PostgreSQL
| Owner | Schema(s) | Others' access |
|---|---|---|
| control-backend | one schema per module (`identity`, `profiles`, `settings`, `agents`, `mandates`, `decisioning`, `ledger`, `payments`, `authorisation`, `reconciliation`, `audit`, `activity`, `notifications`) plus the event publication registry | hook-responder: `SELECT` on published views of `authorisation`, `agents` and `settings` only |
| hook-responder | `hook`: insert-only call log and pre-approval consumption ([06-consistency.md](06-consistency.md) §4) | control-backend reads it by cursor for reconciliation |
| agent-harness | its own database (`harness`): conversations, memory | none; control data is not reachable from here |

The audit tables are insert-only: the roles lack `UPDATE` and `DELETE`, and a trigger rejects them.

**Personal data and erasure.** The audit store is insert-only, yet it holds whole conversations, and the bank as data controller may have to erase a cardholder's data. Retention and erasure are **deferred** (user, 2026-10-29): the design keeps the option open by never putting personal text into a key, index or hash input, and by recording the cardholder id on every audit row, so a later erasure mechanism (per-cardholder encryption with key deletion is the candidate) touches only the storage adapter. The `profiles` schema holds the buyer, traveller and delivery data a checkout needs; other modules and the `DecisionInput` keep only its version ids, so erasing a profile does not break a replay. Open item in [05-open-items.md](05-open-items.md).

**The control backend and the hook responder must share one PostgreSQL instance** (a bank may run it highly available, but not split it): the hook reads a pre-approval the moment its decision commits, and consumes it under a unique constraint. Splitting them would need a replicated pre-approval feed and a different consumption design; that is a new decision, not configuration. The harness's database may live on a separate instance.

## The backend's outbound calls to merchants
The trusted backend talks to merchants (checkout sessions, product records, offer searches for item specs). That is the one place where the trusted zone reads from the outside world, so it is fenced: endpoints come only from the merchant directory, which is filled from the networks' merchant registries (and the demo shop in the PoC); the agent supplies a merchant reference, never a URL; responses are parsed with size limits and strict schemas into typed fields; free text inside them is `UntrustedText`; private address ranges and redirects are refused. Because each proposal opens a session at the merchant (for an airline, possibly a seat hold), proposals are rate-limited per agent instance and per mandate in `settings`.

## The hook responder's scope and failure behaviour
- **Which authorisations:** only those on network tokens enrolled through us (the token references in `agents`). For any other token or card the answer is **no opinion**, mapped to whatever the processor profile uses for "let the issuer's own rules decide". It never declines a cardholder's ordinary purchases.
- **No matching pre-approval for an enrolled agent token:** shadow mode records "would decline". Enforcing mode declines with the processor's code for a cardholder-elected control, and records the authorisation for reconciliation.
- **Currency:** the pre-approval stores the approved amount in the merchant's transaction currency. The match compares against the authorisation's **transaction** amount and currency, never the cardholder's billing amount. A profile that only carries the billing amount is not supported until a tolerance rule is decided with that bank.
- **Database unavailable or budget exceeded:** the answer is the bank's configured fallback (`settings`: no opinion, or decline agent tokens), and the call is logged locally and replayed into `hook` when the database returns.
- **Mode and kill switch:** set by the bank in `settings` through the console; the hook responder reads them from a published view and caches them for at most a few seconds.

## Secrets
| Secret | Lives only in |
|---|---|
| Cybersource keys, network signing keys (TAP, Web Bot Auth for checkout), message-level encryption keys | control-backend |
| LLM gateway key | agent-harness |
| Intent model key | control-backend |
| Processor hook secrets | hook-responder |
| Agent session and run token signing key | control-backend (`identity`) |
| Agent-instance credential (claims due runs only) | agent-harness |
| E-mail or web-push sender credentials (`Notifier` adapter) | control-backend (`notifications`) |

Never in a repository; only the names of the settings that hold them.

## Deployment
- **PoC and development:** `docker compose` on the Pi: PostgreSQL, control-backend, control-mcp, hook-responder, agent-harness, a reverse proxy serving the web build, plus a `sim` profile with the simulators and the demo shop. Each JVM gets an explicit memory cap; check `free -h` first.
- **Our EU cloud or inside a bank's perimeter:** the same OCI images, one instance per bank. What differs is configuration: which network adapters are live, the processor profile, the identity provider, and whether model calls egress through the bank's gateway. Simulator images are never part of these deployments.
