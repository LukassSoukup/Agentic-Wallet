#!/usr/bin/env bash
# T-037 walking skeleton on the Pi: starts PostgreSQL in compose and every deployable with its heap
# cap, runs the scripted purchase through control-mcp, loads the hook responder, kills the backend
# mid-checkout and watches the outbox and resolver settle it after the restart, checks that one
# pre-approval pays once, and records start times and memory. Throwaway measurement for the spike.
#
# Needs: ./mvnw verify (or package) run first, and .env with POSTGRES_PASSWORD and SKELETON_AGENT_TOKEN
# (both synthetic). Leaves nothing behind: the exit trap stops every JVM and removes the compose database.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-25}"
JAVA="$JAVA_HOME/bin/java"
STAMP="$(date +%Y-%m-%d_%H%M)"
OUT="measurements/skeleton_$STAMP.md"
LOGS="measurements/skeleton_$STAMP"
SETTLE="${SETTLE:-20}"
HOOK_REQUESTS="${HOOK_REQUESTS:-1000}"
HOOK_CONCURRENCY="${HOOK_CONCURRENCY:-8}"
mkdir -p "$LOGS"

set -a; [[ -f .env ]] && . ./.env; set +a
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-synthetic-spike-password}"
export SKELETON_AGENT_TOKEN="${SKELETON_AGENT_TOKEN:-synthetic-agent-token-0001}"
PG_PORT="${POSTGRES_PORT:-15432}"
export CONTROL_DB_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${POSTGRES_DB:-control}"
export CONTROL_DB_USER="${POSTGRES_USER:-control}" CONTROL_DB_PASSWORD="$POSTGRES_PASSWORD"
export HOOK_DB_USER="$CONTROL_DB_USER" HOOK_DB_PASSWORD="$POSTGRES_PASSWORD" HOOK_MODE=SHADOW
BACKEND=http://localhost:18080; MCP=http://localhost:8081; HOOK=http://localhost:8082
GATEWAY_HOSTED=http://localhost:8083; HARNESS_PORT=8084; SHOP=http://localhost:8090
port_busy() { ss -ltnH | awk '{print $4}' | sed 's/.*://' | grep -qx "$1"; }
for p in 18080 8081 8082 8083 8084 8090; do port_busy "$p" && { echo "port $p is in use on this host; stop that service or change the port map in this script"; exit 1; }; done
# the standalone connector-gateway JVM (T-008's, for comparison) takes the first free port of these
GATEWAY_ALONE_PORT=""; for p in 8085 8185 8285 8385; do port_busy "$p" || { GATEWAY_ALONE_PORT=$p; break; }; done
[[ -n "$GATEWAY_ALONE_PORT" ]] || { echo "no free port for the standalone connector-gateway"; exit 1; }
GATEWAY_ALONE="http://localhost:$GATEWAY_ALONE_PORT"
export CONTROL_BACKEND_URL="$BACKEND" CONTROL_MCP_URL="$MCP" DEMO_SHOP_URL="$SHOP" NETWORK_URL="$SHOP"
export HOOK_RESPONDER_URL="$HOOK" GATEWAY_PORT=8083 OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-unset}"

JVM_FLAGS=(-Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -Xshare:auto)
declare -A PIDS=()

cleanup() {
  for name in "${!PIDS[@]}"; do kill "${PIDS[$name]}" 2>/dev/null || true; done
  wait 2>/dev/null || true
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_q() { docker compose exec -T postgres psql -U "$CONTROL_DB_USER" -d "${POSTGRES_DB:-control}" -tAc "$1"; }

# start <name> <jar> <heap> <port> [extra java args...]; records context start and RSS after SETTLE seconds
start() {
  local name="$1" jar="$2" heap="$3" port="$4"; shift 4
  local log="$LOGS/$name.log"
  PORT="$port" "$JAVA" -Xmx"$heap" "${JVM_FLAGS[@]}" "$@" -jar "$jar" > "$log" 2>&1 &
  PIDS[$name]=$!
  for _ in $(seq 1 120); do grep -q "Started .* in " "$log" 2>/dev/null && break; sleep 1; done
  if ! grep -q "Started .* in " "$log"; then echo "$name failed to start:"; tail -20 "$log"; exit 1; fi
  echo "started $name on $port (pid ${PIDS[$name]})"
}

measure() {
  local name="$1" heap="$2"; local log="$LOGS/$name.log"; local pid="${PIDS[$name]}"
  local ctx jvm rss_kb heap_used
  ctx=$(grep -o "Started .* in [0-9.]* seconds" "$log" | head -1 | grep -o "in [0-9.]*" | cut -d' ' -f2)
  jvm=$(grep -o "process running for [0-9.]*" "$log" | head -1 | grep -o "[0-9.]*$")
  rss_kb=$(grep VmRSS "/proc/$pid/status" | awk '{print $2}')
  heap_used=$("$JAVA_HOME/bin/jcmd" "$pid" GC.heap_info 2>/dev/null | grep -o "used [0-9]*K" | head -1 | grep -o "[0-9]*" || echo 0)
  echo "| $name | $heap | $ctx | $jvm | $(( rss_kb / 1024 )) | $(( heap_used / 1024 )) |" >> "$OUT"
}

{
  echo "# T-037 walking skeleton on the Pi ($(date -Iseconds))"
  echo
  echo "Host: $(uname -m), $(nproc) cores, $("$JAVA" -version 2>&1 | head -1). Home-server containers running alongside."
  echo
  echo '## free -h before (PostgreSQL in compose already up)'
  echo '```'
} > "$OUT"
docker compose up -d --wait postgres
free -h >> "$OUT"; echo '```' >> "$OUT"

start control-backend control-backend/target/control-backend-0.0.1-SNAPSHOT.jar 384m 18080
start hook-responder hook-responder/target/hook-responder-0.0.1-SNAPSHOT.jar 128m 8082
start demo-shop simulators/target/simulators-0.0.1-SNAPSHOT-exec.jar 128m 8090
start control-mcp control-mcp/target/control-mcp-0.0.1-SNAPSHOT.jar 192m 8081
start connector-gateway connector-gateway/target/connector-gateway-0.0.1-SNAPSHOT.jar 192m "$GATEWAY_ALONE_PORT"
start agent-harness agent-harness/target/agent-harness-0.0.1-SNAPSHOT.jar 256m "$HARNESS_PORT" -Dspring.profiles.active=skeleton

echo "settling ${SETTLE}s before reading memory"; sleep "$SETTLE"
{
  echo
  echo '## Start time and memory per JVM'
  echo
  echo "| Deployable | -Xmx | Context start (s) | Process running at Started (s) | RSS after ${SETTLE}s (MB) | Heap used (MB) |"
  echo '|---|---|---|---|---|---|'
} >> "$OUT"
measure control-backend 384m; measure hook-responder 128m; measure demo-shop 128m
measure control-mcp 192m; measure connector-gateway 192m; measure agent-harness 256m
{
  echo
  echo "control-mcp hosts the connector gateway on its second listener ($GATEWAY_HOSTED); connector-gateway is the separate JVM of T-008 for comparison."
  echo
  echo '## free -h with every JVM and PostgreSQL up'
  echo '```'
  free -h
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep postgres | sed 's/^/postgres container: /' || true
  echo '```'
} >> "$OUT"

echo "== scripted purchase through control-mcp (the harness ran it at start)"
for _ in $(seq 1 60); do grep -q "get_decision: .* checkout \(COMPLETED\|FAILED\|UNKNOWN\)" "$LOGS/agent-harness.log" 2>/dev/null && break; sleep 1; done
{
  echo
  echo '## Scenario A: one purchase end to end, harness → control-mcp → backend → demo shop → processor → hook'
  echo '```'
  grep -o "propose_purchase: .*" "$LOGS/agent-harness.log" || echo "propose_purchase: no line found"
  grep -o "get_decision: .*" "$LOGS/agent-harness.log" | tail -1 || true
  echo "hook call log: $(psql_q "select answer || ' ' || processor_authorisation_id from hook.call_log order by received_at desc limit 1")"
  echo '```'
} >> "$OUT"

echo "== Scenario B: hook load"
HOOK_LINE=$("$JAVA" skeleton/HookLoad.java "$HOOK" "$HOOK_REQUESTS" "$HOOK_CONCURRENCY")
{ echo; echo '## Scenario B: hook responder latency'; echo '```'; echo "$HOOK_LINE"; echo '```'; } >> "$OUT"

echo "== Scenario C: kill the backend mid-checkout"
curl -sS -X POST "$SHOP/control/completion-delay" -H 'Content-Type: application/json' -d '{"completionDelayMillis":8000}' >/dev/null
PROPOSAL=$(curl -sS -X POST "$BACKEND/api/v1/agent/proposals" -H "Authorization: Bearer $SKELETON_AGENT_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"purchaseKey\":\"kill-$(date +%s)\",\"mandateId\":\"00000000-0000-0000-0000-00000000a001\",\"merchantReference\":\"demo-shop\",\"lines\":[{\"merchantItemId\":\"SYN-0001\",\"quantity\":1}],\"description\":\"kill scenario\"}")
DECISION=$(echo "$PROPOSAL" | grep -o '"decisionId":"[^"]*"' | cut -d'"' -f4)
echo "decision $DECISION: $PROPOSAL"
for _ in $(seq 1 30); do [[ "$(psql_q "select state from payments.checkout_attempt where decision_id = '$DECISION'")" == "COMPLETING" ]] && break; sleep 0.2; done
STATE_AT_KILL=$(psql_q "select state from payments.checkout_attempt where decision_id = '$DECISION'")
kill -9 "${PIDS[control-backend]}"; wait "${PIDS[control-backend]}" 2>/dev/null || true; unset 'PIDS[control-backend]'
KILLED_AT=$(date +%s.%N)
echo "killed the backend with the attempt in $STATE_AT_KILL"
curl -sS -X POST "$SHOP/control/reset" >/dev/null
mv "$LOGS/control-backend.log" "$LOGS/control-backend-before-kill.log"
start control-backend control-backend/target/control-backend-0.0.1-SNAPSHOT.jar 384m 18080
RESTARTED_AT=$(date +%s.%N)
for _ in $(seq 1 90); do [[ "$(psql_q "select state from payments.checkout_attempt where decision_id = '$DECISION'")" =~ ^(COMPLETED|FAILED)$ ]] && break; sleep 1; done
SETTLED_AT=$(date +%s.%N)
FINAL_STATE=$(psql_q "select state from payments.checkout_attempt where decision_id = '$DECISION'")
CREDENTIALS=$(curl -sS "$SHOP/network/credentials/$DECISION/requests")
RESERVATION=$(psql_q "select state from ledger.reservation where decision_id = '$DECISION'")
PUBLICATIONS=$(psql_q "select count(*) from event_publication where completion_date is null")
{
  echo
  echo '## Scenario C: backend killed (SIGKILL) mid-checkout, restarted'
  echo '```'
  echo "attempt state at kill: $STATE_AT_KILL"
  echo "final attempt state: $FINAL_STATE after $(echo "$SETTLED_AT - $RESTARTED_AT" | bc) s from the restart's Started line (process down for $(echo "$RESTARTED_AT - $KILLED_AT" | bc) s)"
  echo "credential requests at the network for this decision: $CREDENTIALS"
  echo "reservation: $RESERVATION; incomplete outbox publications now: $PUBLICATIONS"
  echo "redeliveries fenced after the restart: $(grep -c 'is past CREATED; this delivery changes nothing' "$LOGS/control-backend.log" || true)"
  echo "transitions after the restart: $(grep -o 'moved [A-Z_]* -> [A-Z_]*' "$LOGS/control-backend.log" | tr '\n' ';')"
  echo '```'
} >> "$OUT"

echo "== Scenario D: two authorisations against one pre-approval"
FIRST_AUTH=$(psql_q "select processor_authorisation_id from hook.call_log where answer = 'MATCH' order by received_at desc limit 1")
AMOUNT=$(psql_q "select amount from hook.call_log where processor_authorisation_id = '$FIRST_AUTH'")
SECOND=$(curl -sS -X POST "$SHOP/processor/authorisations" -H 'Content-Type: application/json' \
  -d "{\"processorAuthorisationId\":\"second-$(date +%s)\",\"amount\":\"$AMOUNT\",\"currency\":\"EUR\",\"merchantReference\":\"demo-shop\",\"occurredAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}")
REPLAY=$(curl -sS -X POST "$SHOP/processor/authorisations" -H 'Content-Type: application/json' \
  -d "{\"processorAuthorisationId\":\"$FIRST_AUTH\",\"amount\":\"$AMOUNT\",\"currency\":\"EUR\",\"merchantReference\":\"demo-shop\",\"occurredAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}")
{
  echo
  echo '## Scenario D: one pre-approval pays once'
  echo '```'
  echo "first authorisation $FIRST_AUTH matched (from the completed purchases above)"
  echo "a second authorisation for the same amount at the same merchant: $SECOND"
  echo "the first authorisation id delivered again: $REPLAY"
  echo "consumptions: $(psql_q "select count(*) from hook.pre_approval_consumption"); matches in the call log: $(psql_q "select count(*) from hook.call_log where answer = 'MATCH'")"
  echo '```'
} >> "$OUT"

echo "written $OUT"
