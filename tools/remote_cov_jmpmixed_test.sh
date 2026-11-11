#!/bin/bash
set -u
LOADER=${1:?Loader fabric oder neoforge}
NEW_JAR=${2:?Jar von Just More Parts 1.2.0}
OLD_JAR=${3:?Jar von Just More Parts 1.1.1}
SLOTS=${PROPWASH_MC_SLOTS:-}
ROOT=$(pwd)
OUT=${COV_JMPMIXED_OUT:-$ROOT/build/cov-jmpmixed}
SERVER_DIR=$ROOT/$LOADER/runs/smoke-server
CLIENT_DIR=$ROOT/$LOADER/runs/smoke-client
mkdir -p "$OUT" "$SERVER_DIR/mods" "$CLIENT_DIR/mods"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}

run() {
  if [ -n "$SLOTS" ]; then
    "$SLOTS" "$@"
  else
    "$@"
  fi
}

place() {
  rm -f "$SERVER_DIR/mods/"justmoreparts-*.jar "$CLIENT_DIR/mods/"justmoreparts-*.jar
  cp "$1" "$SERVER_DIR/mods/"
  cp "$2" "$CLIENT_DIR/mods/"
}

clean() {
  rm -f "$SERVER_DIR/mods/"justmoreparts-*.jar "$CLIENT_DIR/mods/"justmoreparts-*.jar "$SERVER_DIR/jointest.stop"
}
trap clean EXIT

kill_tree() {
  local pid
  for pid in $(pgrep -P "$1"); do
    kill_tree "$pid"
  done
  kill "$1" 2>/dev/null
}

lock_message() {
  grep -h -E -o "(is incompatible with any version before 1\.2\.0- of mod 'Propwash: Just More Parts'[^!]*!|Just More Parts 1\.1\.x does not work with Propwash 0\.6\.0[^)]*\)|Mod propwash is incompatible with justmoreparts below [0-9A-Za-z.-]+)" "$@" 2>/dev/null | head -1
}

exceptions() {
  grep -E -c "Exception|/ERROR\]" "$1" 2>/dev/null || true
}

wait_for() {
  local file=$1 pid=$2 seconds=$3 pattern=$4
  for _ in $(seq 1 "$seconds"); do
    grep -E -q "$pattern" "$file" 2>/dev/null && return 0
    kill -0 "$pid" 2>/dev/null || return 1
    sleep 1
  done
  return 1
}

case_server_new_client_old() {
  local name=$LOADER-server120-client111
  place "$NEW_JAR" "$OLD_JAR"
  rm -f "$SERVER_DIR/jointest.stop"
  PROPWASH_JOINTEST_HOLD=900 run ./gradlew ":$LOADER:runServerSmoke" > "$OUT/$name-server.out" 2>&1 &
  local server=$!
  if ! wait_for "$OUT/$name-server.out" $server 900 "Starting Minecraft server on"; then
    echo "$name: Server startet nicht" | tee "$OUT/$name.txt"
    kill_tree $server
    return 1
  fi
  local port
  port=$(grep -E -o "Starting Minecraft server on [^ ]*:[0-9]+" "$OUT/$name-server.out" | head -1 | grep -E -o "[0-9]+$")
  CI=1 PROPWASH_JOINTEST=127.0.0.1:$port PROPWASH_JOINTEST_LABEL=jmp-mixed PROPWASH_JOINTEST_EXPECT='Just More Parts|1.2.0|1.1.1|!joined' \
    run ./gradlew ":$LOADER:runClientSmoke" > "$OUT/$name-client.out" 2>&1 &
  local client=$!
  wait_for "$OUT/$name-client.out" $client 900 "Incompatible mods found|is incompatible with|BUILD (FAILED|SUCCESSFUL)"
  sleep 5
  local message
  message=$(lock_message "$OUT/$name-client.out" "$CLIENT_DIR/logs/latest.log")
  local joined
  joined=$(grep -c "\[Beitrittstest\].*joined" "$OUT/$name-client.out")
  if kill -0 $client 2>/dev/null; then
    kill_tree $client
  fi
  wait $client 2>/dev/null
  touch "$SERVER_DIR/jointest.stop"
  wait $server
  {
    echo "fall $name: Server mit $(basename "$NEW_JAR"), Client mit $(basename "$OLD_JAR")"
    echo "client_message=${message:-keine}"
    echo "client_joined=$joined"
    echo "server_log_exceptions=$(exceptions "$SERVER_DIR/logs/latest.log")"
  } | tee "$OUT/$name.txt"
  [ -n "$message" ] && [ "$joined" = 0 ]
}

case_server_old() {
  local name=$LOADER-server111-client120
  place "$OLD_JAR" "$NEW_JAR"
  rm -f "$SERVER_DIR/jointest.stop"
  PROPWASH_JOINTEST_HOLD=120 run ./gradlew ":$LOADER:runServerSmoke" > "$OUT/$name-server.out" 2>&1 &
  local server=$!
  wait_for "$OUT/$name-server.out" $server 900 "Incompatible mods found|is incompatible with|Starting Minecraft server on|BUILD (FAILED|SUCCESSFUL)"
  sleep 5
  local message started
  message=$(lock_message "$OUT/$name-server.out" "$SERVER_DIR/logs/latest.log")
  started=$(grep -c "Starting Minecraft server on" "$OUT/$name-server.out")
  if kill -0 $server 2>/dev/null; then
    touch "$SERVER_DIR/jointest.stop"
    sleep 10
    kill -0 $server 2>/dev/null && kill_tree $server
  fi
  wait $server 2>/dev/null
  {
    echo "fall $name: Server mit $(basename "$OLD_JAR"), Client mit $(basename "$NEW_JAR")"
    echo "server_message=${message:-keine}"
    echo "server_started=$started"
  } | tee "$OUT/$name.txt"
  [ -n "$message" ] && [ "$started" = 0 ]
}

status=0
case_server_new_client_old || status=1
case_server_old || status=1
exit $status
