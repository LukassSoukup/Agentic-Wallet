# 07 Walking skeleton: what T-037 confirmed on the Pi

Report of the walking skeleton spike (T-037), built in [`spikes/toolchain/`](../../spikes/toolchain/) on top of the toolchain spike of T-008 and run on the Raspberry Pi 5 on 2026-11-01. The spike exists to confirm [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md) and the mechanics of [06-consistency](06-consistency.md) before production code starts. **Everything under `spikes/` is throwaway** (the user, 2026-11-01): T-009 builds the real reactor at the root as new structure, takes from the spike only what the design intends, piece by piece with its dependencies, and nothing by copy. This page says what was proved, what T-009 must rebuild, and what it may read as a reference.

## What runs

One trivial purchase end to end across the four backend deployables, the simulators and PostgreSQL 18:

```
agent-harness (scripted driver, no model) ──MCP streamable HTTP, bearer header──▶ control-mcp
control-mcp ──REST /api/v1/agent/proposals, header forwarded verbatim──▶ control-backend
control-backend: proposal seen? → demo shop opens a priced session → ONE TRANSACTION under the
  budget-period row lock: proposal row (unique key), decision, reservation, pre-approval, audit
  record with the complete DecisionInput, outbox row → DecisionMade
outbox listener (payments): CREATED → CREDENTIAL_REQUESTED → network credential →
  COMPLETING → demo shop completes → COMPLETED + ledger.count + CheckoutCompleted
demo shop → processor simulator → hook-responder: matches the pre-approval from the published
  view, consumes it under the unique key, logs the call
```

The scripted driver is a test harness, not a shopping agent; the LLM is not on the decision path and was not called.

## Confirmed, with the evidence

| Claim (ADR-0006, 06-consistency, ADR-0009) | Evidence | Where |
|---|---|---|
| A pure `decide(input)` in `control-core` replays the stored verdict from the audit record alone (invariant 8) | `PurchaseEndToEndTest.approvesPaysOnceAndReplays` reads `audit.decision_record` and runs `Decider.decide(storedInput)`; `DeciderPropertyTest` (jetCheck) for determinism and caution | control-core, control-backend |
| Decision, reservation, pre-approval, audit record and outbox row commit in one transaction (§1, §8 of 04) | `ProposalService.decideAndCommit` in one `TransactionTemplate`; ledger, authorisation and audit APIs are `@Transactional(MANDATORY)`, so calling them outside a transaction fails at runtime | control-backend |
| Two proposals racing for one budget: exactly one APPROVE (§1) | `BudgetRaceTest`: two 89.00 EUR proposals against 150.00 EUR released on one latch; `SELECT … FOR UPDATE` on the budget-period row serialises them; the loser decides on the updated ledger and gets `OVER_BUDGET` | control-backend |
| The same `purchaseKey` delivered twice at once: one decision, one proposal row, the same answer twice (§5) | `DuplicateDeliveryTest`: PostgreSQL blocks the second insert of the unique key until the first transaction commits, then it fails and the loser re-reads the stored decision; a reworded retry returns the stored decision, different binding fields are a conflict | control-backend |
| A timeout is `UNKNOWN`, never `FAILED`; the reservation and pre-approval stay; the resolver settles it by reading the session status (§3) | `CheckoutTimeoutTest`: the demo shop completes but answers after the read timeout; the attempt goes `UNKNOWN`, the resolver finds the session `COMPLETED` after the stored deadline and counts once; one credential request | control-backend |
| A redelivery never changes a mid-call attempt's state (B6 fence) | `RedeliveryFenceTest` resubmits the incomplete `DecisionMade` publication through `IncompleteEventPublications` while the listener is in `COMPLETING`; the second delivery finds the attempt past `CREATED` and returns; one credential, one count | control-backend |
| A killed process mid-checkout: never two credential requests, the final state is right (§3) | Scenario C of `skeleton/run.sh`: SIGKILL with the attempt in `COMPLETING`, restart, republish-on-restart delivers `DecisionMade` again, the fence holds, the resolver completes it; the network counted one credential request | Pi run, below |
| One pre-approval matches one authorisation; the same processor id twice gets the stored answer (§4) | `HookResponderTest` against PostgreSQL 18 (consumption under the unique key with `ON CONFLICT DO NOTHING`, call log by processor id); Scenario D on the Pi with a real second authorisation and a replay | hook-responder, Pi run |
| Every step-up outcome is a second decision (§2) | **not exercised**: the skeleton has no step-up; T-003 and the thin slice | — |
| Kernel events keep modules that listen to each other acyclic under Spring Modulith (05-open-items) | `ModularityTest` passes with `payments` listening to `MandateRevoked` and `mandates` listening to `CheckoutCompleted`, both records in `shared-kernel.events`; `RevocationTest` runs the pair | control-backend |
| The agent's identity comes from the credential, never the body, through `control-mcp` (ADR-0006, B1) | `PurchaseToolsTest`: the bearer header travels through the MCP SDK's transport context into the tool handler and on to the backend verbatim; a missing header is a typed error result and never reaches the backend; `AgentApiTest`: the backend resolves the token and refuses an unknown one with a typed problem | control-mcp, control-backend |
| The connector gateway can be hosted inside `control-mcp` behind its own listener (ADR-0008, T-037) | `HostedConnectorGatewayTest`: a second MCP server with its own transport on a second Tomcat connector; each listener serves only its own endpoint; the tool lists are disjoint | control-mcp |
| The staleness monitor's bound must exceed the listener deadline (B6) and start-up refuses otherwise (B5) | `ListenerDeadlineCheckTest`; the scenario context computes a 10.5 s deadline under a 12 s bound | control-backend |
| Hostile text in agent-supplied ids never reaches a log or a message (B4) | `IdFormatTest`, `UntrustedTextTest`; `AgentApiTest` and `HookResponderTest` assert a bad id is refused without echoing it | shared-kernel, both edges |

## Measurements on the Pi

Filled from `spikes/toolchain/measurements/skeleton_<timestamp>.md`, written by `skeleton/run.sh`.

Measured on 2026-11-01 by `spikes/toolchain/skeleton/run.sh` ([`measurements/skeleton_2026-11-01_2142.md`](../../spikes/toolchain/measurements/skeleton_2026-11-01_2142.md)): every packaged jar started against PostgreSQL 18 in compose with its heap cap and `-Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -Xshare:auto`, resident memory read 20 s after "Started" with the purchase flow already run. The Pi was running its home-server containers at the same time (`used` 8.2 GiB before the first JVM).

### Start time and memory per JVM

| Deployable | `-Xmx` | Context start (s) | RSS (MB) | Heap used (MB) | T-008 idle RSS (MB) |
|---|---|---|---|---|---|
| control-backend (JPA, Flyway, Modulith, the flow) | 384m | 11.0 | 292 | 62 | 260 |
| control-mcp (MCP server, purchase tools, hosted connector gateway on a second listener) | 192m | 5.2 | 210 | 18 | 194 |
| hook-responder (JdbcClient, Flyway for `hook`) | 128m | 4.9 | 176 | 28 | 154 |
| agent-harness (MCP client, scripted driver) | 256m | 9.0 | 246 | 20 | 235 |
| demo-shop, network and processor simulators | 128m | 3.7 | 172 | 35 | 148 |
| connector-gateway as its own JVM (T-008's, for comparison only) | 192m | 4.2 | 182 | 15 | 183 |
| **the four deployables plus the simulators** | | | **1 096** | | |
| PostgreSQL 18 container | | | 58 (of the 512 limit) | | 48 |

`free -h` went from 8.2 GiB used to 9.4 GiB with all six JVMs and PostgreSQL up. Reading: the caps of T-008 hold with the flow running; resident memory grew 15 to 30 MB per JVM against idle, still dominated by metaspace and code cache rather than heap. **Hosting the connector gateway inside `control-mcp` costs at most about 16 MB of resident memory** (210 against T-008's 194, measured idle and without the purchase tools, so the 16 MB is an upper bound, not the gateway alone), **while a fifth JVM costs 182 MB**: the gateway stays hosted until epic K needs it apart (ADR-0008). Start times are unchanged from T-008: 4 to 11 s with C1 only; the backend's 11 s include Flyway and Hibernate validation.

### Scenario A: one purchase through `control-mcp`

`propose_purchase` answered APPROVE with its three reasons in **1 584 ms** end to end on a cold JVM (MCP call → REST → demo-shop session → decision → one transaction → outbox row; a first call pays for class loading and JIT on every hop); `get_decision` saw the checkout `COMPLETED` on its second poll, one second later; the hook's call log shows the merchant's authorisation `MATCH`ed the pre-approval.

### Scenario B: hook responder latency

| Requests | Concurrency | p50 | p95 | p99 | max | over 300 ms |
|---|---|---|---|---|---|---|
| 1 000 | 8 | 26 ms | 52 ms | 75 ms | 150 ms | 0 |

Each request was a fresh synthetic authorisation that matched nothing, so it walked the whole path (deduplication lookup, view query, call-log insert) on a 3-connection pool, with a 128 MB heap, on the Pi beside five other JVMs. p99 is a quarter of the 300 ms budget in [ADR-0006](../adr/ADR-0006-architecture-style-and-module-cut.md); the processor's real budget (800 ms to 2 s) is further still.

### Scenario C: the backend killed mid-checkout

The demo shop was told to answer completions 8 s late; a proposal was approved over REST; when the attempt reached `COMPLETING` the backend got `SIGKILL`. It was down for 11.1 s. After the restart, Spring Modulith republished the incomplete `DecisionMade`; the listener found the attempt past `CREATED` and changed nothing ("this delivery changes nothing" in the log); the resolver's first tick found the stored deadline passed, read the session as completed at the merchant and moved `COMPLETING → COMPLETED` **0.12 s after the restart's "Started" line**. The network counted **one** credential request for the decision, the reservation ended `COUNTED`, and no outbox publication was left incomplete.

### Scenario D: one pre-approval pays once

Against the pre-approval of a completed purchase, a second authorisation with a new processor id for the same amount at the same merchant answered `NO_MATCH`; the first authorisation id delivered again answered `MATCH` with the same pre-approval; two purchases in the run left exactly two consumptions and two matches in the call log.

### MCP tool calls over streamable HTTP

The harness's autoconfigured client called `propose_purchase` and `get_decision` over streamable HTTP with the bearer header on every request; `control-mcp` forwarded it to the backend over REST; the hosted gateway answered on its own listener. Call latency is the Scenario A line above; the T-008 numbers for the SDK (tool list, `tools/list_changed`, client certificate) stand.

## Findings that change or sharpen the design

1. **`@ApplicationModuleListener` conflicts with §3.** Spring Modulith's convenience annotation wraps the whole listener in one `REQUIRES_NEW` transaction, while §3 needs each transition committed before the remote call it announces. The checkout listener is therefore a plain `@Async @TransactionalEventListener(AFTER_COMMIT)` with explicit `TransactionTemplate` steps; the publication registry still tracks and completes it (the scenario tests end with zero incomplete publications). Rule for T-003 and T-009: listeners that make remote calls never use `@ApplicationModuleListener`; listeners that only write (audit, read models, the completed-purchase counter) may.
2. **What the registry records after a hung listener returns is not stable.** T-008 (3 s bound, 8 s hang) saw the late return overwrite the monitor's `FAILED` with `COMPLETED`; T-037 (6 s bound, 12 s hang) saw `FAILED` stay. The resubmission job must therefore treat a publication that flips either way as normal, and nothing in the design may depend on the registry's answer for a listener that outlived its bound: the attempt's own fence (next item) is what prevents double work, and `ListenerDeadlineCheck` keeps the bound above every listener's deadline so the case stays theoretical.
3. **An event nobody listens to leaves no trace in the registry.** Spring Modulith writes a publication row per (event, listener); `CheckoutOutcomeUnknown` had no listener in the skeleton (`activity` and `notifications` do not exist yet) and so left no row, while `CheckoutCompleted`, consumed by `mandates`, did. For T-003's event catalogue: every event must name at least one consumer, or its publication is invisible to the outbox, the console and any replay of "what was announced"; the scenario tests use a recording listener as the stand-in.
4. **A revocation could race a first decision and still pay** (found by the review of this branch, fixed here). The mandate terms were read before the merchant call and decided on under the budget lock only; a revocation in between left an APPROVE with an ACTIVE mandate in its input and an attempt that was created after `MandateRevoked` had already cancelled nothing. Now the decision transaction share-locks the mandate version after the budget lock (`SELECT … FOR SHARE`, so `revoke`'s UPDATE waits for the commit or is seen), and the attempt's first transition re-reads the mandate under the same lock and moves `CREATED → CANCELLED` instead when it was revoked (`RevokedBeforeCredentialTest`). 06-consistency §1 and §3 now say so; T-003 writes the rule.
5. **Resubmission can redeliver to a listener that is still running.** The staleness monitor marks a publication `FAILED` while its listener runs (T-008), and both the failed-publications job and `resubmitIncompletePublications` then deliver it again. The registry does not prevent this; the attempt's fenced transitions and idempotent listeners make it harmless (`RedeliveryFenceTest` now also asserts that the second delivery happened). 06-consistency §7 says so.
6. **The resolver must never fail an attempt on an ambiguous merchant answer** (review finding, fixed here). "Session not found" and "already completed" are not evidence that nothing was paid: the first is now `UNKNOWN` until the pre-approval window closes (T-003 closes it), the second is a port mode of its own, `AlreadyCompleted`, resolved through the session status.
7. **The attempt row's deadline and compare-and-set are the whole fence.** Every transition is `UPDATE … WHERE state = expected`; under `READ COMMITTED` PostgreSQL re-checks the condition after the row lock, so exactly one of two racing transitions wins. No `SELECT … FOR UPDATE` on the attempt is needed. B6 stands as written; 06-consistency §3 should say the transitions are plain conditional updates.
8. **Insert the proposal row before taking the budget lock.** Then a twin delivery fails on the unique key without ever waiting for the budget-period lock, and the budget lock is held only by the delivery that will decide. 06-consistency §5 should fix this order.
9. **`DecisionMade` needs the stored session expiry.** The attempt row stores `session_expires_at` for the resolver's "no longer completable" rule; the skeleton took it from configuration because the event did not carry it. T-003: the event (or the decision record the listener reads) carries the stored checkout session expiry.
10. **Kernel events pass Spring Modulith's verification** as the way to keep `payments` ↔ `mandates` acyclic in code: the open item in [05-open-items](05-open-items.md) is closed.
11. **The bearer header reaches the tool handler through the MCP SDK's transport context.** Spring AI's WebMvc streamable transport takes a `contextExtractor`, and its autoconfigured provider yields to a bean of the same type, so no fork or filter is needed. The connector gateway hosted in `control-mcp` must be built by hand (`McpServer.sync(...)`), because Spring AI's autoconfiguration folds every `List<SyncToolSpecification>` bean into the one control server, injects the transport provider by type (so the control provider must be `@Primary` and the gateway's wired by qualifier), and backs off its own `McpSyncServer` bean when any other exists (so the gateway's server lives in a holder, never as a bean of that type). All facts for epic H and K.
12. **MANDATORY propagation documents and enforces "inside the caller's transaction".** Ledger reserve/count/release, the pre-approval write and cancel, and the audit append are `@Transactional(propagation = MANDATORY)`: a call outside a transaction is a bug that fails at once, not a silent autocommit.
13. **A merchant that opens no session is not a decision in the skeleton.** 04-purchase-flow says DECLINE "can't buy here"; the skeleton returns a named outcome without an audit row, because nothing binding exists to decide on and nothing is paid. T-003's rule catalogue must say which it is (an audited DECLINE keeps invariant 1's "every decision is logged" simple; a named error keeps the audit free of non-decisions).
14. **Timeouts on every connection, from configuration.** `spring.datasource.hikari.connection-init-sql` sets PostgreSQL's `transaction_timeout` per connection, and `ListenerDeadlineCheck` validates the staleness bound against the computed deadline at start-up (B5, B6), both as ADR-0009 asks.
15. **Flyway per owner works on one instance.** The backend owns `decisioning`, `ledger`, `authorisation`, `audit`, `payments`, `mandates`; the hook responder owns `hook` with its own history table (`spring.flyway.schemas: hook`) and reads `authorisation.pre_approval_published`. The spike used one database role; the role-per-owner grants are T-009's.
16. **Toolchain notes for T-009.** Boot 4.1 moved `TestRestTemplate` to `org.springframework.boot.resttestclient` (module `spring-boot-resttestclient`, pulled in by `spring-boot-starter-webmvc-test`) and `MockitoBean` lives in `org.springframework.test.context.bean.override.mockito`. Two Boot applications cannot share one test classpath: both load `application.yaml`, so the simulators started in-process picked up the backend's datasource and failed; the scenario tests start the packaged simulators jar as a process instead, as on the Pi. `@Scheduled` needs `@EnableScheduling` on the application; `@ConfigurationPropertiesScan` for record properties. A reactor without Spring Boot's parent POM must set `<parameters>true</parameters>` on the compiler: Spring 7 reads `@PathVariable` and `@RequestParam` names from the class file only, and every path variable answered 500 until it did (the T-008 spike had no path variables, so it never showed).

## What the skeleton did not exercise

**Known gaps a production build must close, left as they are in the spike:** no ownership scoping (mandates and decisions are looked up by id, any known agent session can read any decision); the hook matches without a token and consumes the smallest pre-approval that fits at the merchant, where the real rule matches the token; the whole-transaction rollback of the decision (JPA store plus `JdbcClient` writes in one `TransactionTemplate`) and the insert-only trigger on the audit table rest on code reading, not on a test; `BudgetRaceTest` and `DuplicateDeliveryTest` release their deliveries together but cannot force the two transactions to overlap, so they prove the outcome, not the lock; the hook's edge reads the host clock directly rather than through the `Clock` port; a currency the merchant quotes that is not the base currency ends in an unaudited error rather than a DECLINE, because the FX port is out of scope.

Step-up and its second decision, `PENDING_USER`, foreign currency and the FX port, the hook's poll-back into `authorisation` (`PreApprovalMatched`), mutual TLS between `control-mcp` and the backend (proven separately in T-008), a real identity module (the skeleton maps configured tokens to a cardholder and an agent instance), the web app, the trace and conversation ingest, and more than one backend process. None of these was in T-037's scope; each is in T-003's contracts or in the epics.

## For T-009: rebuild, do not copy

- **Rebuild as new structure:** the reactor and its quality gates (with the T-008 report as the reference), every module's schema and API from T-003's contracts, the ports with their contract comments, the adapters with the contract test suite per port, the scenario tests above as the first acceptance tests of the thin slice.
- **Read as a reference, then write anew:** the shapes that worked here: the attempt repository's conditional updates, the two-phase listener with `TransactionTemplate`, the proposal-first transaction order, the hook's consumption under `ON CONFLICT DO NOTHING`, the transport-context extractor in `control-mcp`, the offset clock and the simulators as a process in the tests, `skeleton/run.sh` as the shape of a Pi measurement.
- **Take nothing verbatim:** the spike's `Money`, ids and events are slices; T-003 owns the real types.
