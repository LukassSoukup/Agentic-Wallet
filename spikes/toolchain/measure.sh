#!/usr/bin/env bash
# T-008: starts every Spring Boot deployable of the spike with its heap cap against the compose
# PostgreSQL, records context start time and resident memory per JVM, and free -h before/after.
# Throwaway measurement for the spike; not workflow tooling for the repo.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-25}"
JAVA="$JAVA_HOME/bin/java"
OUT="measurements/$(date +%Y-%m-%d_%H%M).md"
SETTLE="${SETTLE:-25}"
set -a; [ -f .env ] && . ./.env; set +a
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-synthetic-spike-password}"
export CONTROL_DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-15432}/${POSTGRES_DB:-control}"
export CONTROL_DB_USER="${POSTGRES_USER:-control}" CONTROL_DB_PASSWORD="$POSTGRES_PASSWORD"
export HOOK_DB_USER="$CONTROL_DB_USER" HOOK_DB_PASSWORD="$POSTGRES_PASSWORD"
export CONTROL_MCP_URL="http://localhost:8081" OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-unset}"

# deployable | jar | heap cap | port
DEPLOYABLES=(
  "control-backend|control-backend/target/control-backend-0.0.1-SNAPSHOT.jar|384m|18080"
  "control-mcp|control-mcp/target/control-mcp-0.0.1-SNAPSHOT.jar|192m|8081"
  "hook-responder|hook-responder/target/hook-responder-0.0.1-SNAPSHOT.jar|128m|8082"
  "connector-gateway|connector-gateway/target/connector-gateway-0.0.1-SNAPSHOT.jar|192m|8083"
  "agent-harness|agent-harness/target/agent-harness-0.0.1-SNAPSHOT.jar|256m|8084"
  "demo-shop|simulators/target/simulators-0.0.1-SNAPSHOT.jar|128m|8090"
)

mkdir -p measurements
{
  echo "# JVM start time and memory on the Pi ($(date -Iseconds))"
  echo
  echo "Host: $(uname -m), $(nproc) cores, JDK $("$JAVA" -version 2>&1 | head -1)"
  echo
  echo '## free -h before (PostgreSQL in compose already up)'
  echo '```'
} > "$OUT"

docker compose up -d --wait postgres
docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep postgres | sed 's/^/postgres container: /' >> "$OUT" || true
free -h >> "$OUT"; echo '```' >> "$OUT"

declare -a PIDS=()
# Leaves nothing behind: every JVM is stopped and the compose PostgreSQL with its volume removed, also on failure.
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  wait 2>/dev/null || true
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

{
  echo
  echo '| Deployable | -Xmx | Context start (s) | Process running at start (s) | RSS after '"$SETTLE"'s (MB) | Heap used (MB) | ECS JSON log |'
  echo '|---|---|---|---|---|---|---|'
} >> "$OUT"

for entry in "${DEPLOYABLES[@]}"; do
  IFS='|' read -r name jar heap port <<< "$entry"
  log="measurements/$name.log"
  PORT="$port" LOG_FORMAT="${LOG_FORMAT:-ecs}" "$JAVA" -Xmx"$heap" -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m \
    -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -Xshare:auto \
    -jar "$jar" > "$log" 2>&1 &
  pid=$!; PIDS+=("$pid")
  for _ in $(seq 1 120); do grep -q "Started .* in " "$log" 2>/dev/null && break; sleep 1; done
  if ! grep -q "Started .* in " "$log"; then echo "| $name | $heap | FAILED TO START | | | | |" >> "$OUT"; tail -5 "$log"; continue; fi
  ctx=$(grep -o "Started .* in [0-9.]* seconds" "$log" | grep -o "in [0-9.]*" | cut -d' ' -f2 || true)
  jvm=$(grep -o "process running for [0-9.]*" "$log" | grep -o "[0-9.]*$" || true)
  # Checklist (e): structured logging. Logback is initialised once per JVM, so the running jar is
  # where the ECS format is checked (a second Spring test context in one Surefire JVM keeps the first format).
  grep -q '"ecs":{"version":' "$log" && ecs=yes || ecs=no
  sleep "$SETTLE"
  rss_kb=$(grep VmRSS "/proc/$pid/status" | awk '{print $2}')
  heap_used=$("$JAVA_HOME/bin/jcmd" "$pid" GC.heap_info 2>/dev/null | grep -o "used [0-9]*K" | head -1 | grep -o "[0-9]*" || echo "")
  heap_mb=$(( ${heap_used:-0} / 1024 ))
  echo "| $name | $heap | $ctx | $jvm | $(( rss_kb / 1024 )) | ${heap_mb} | $ecs |" >> "$OUT"
done

{
  echo
  echo '## free -h with every JVM and PostgreSQL up'
  echo '```'
  free -h
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep postgres | sed 's/^/postgres container: /' || true
  echo '```'
  echo
  echo "JVM flags: -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -Xshare:auto; settle ${SETTLE}s before reading RSS."
} >> "$OUT"
echo "written $OUT"
