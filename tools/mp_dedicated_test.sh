#!/bin/bash
set -u
if [ -n "${PROPWASH_MC_SLOTS:-}" ] && [ -z "${PROPWASH_MP_VIA_SLOTS:-}" ] \
   && [ "$("$PROPWASH_MC_SLOTS" --classify "$0" 2>/dev/null)" = none ]; then
  export PROPWASH_MP_VIA_SLOTS=1
  exec "$PROPWASH_MC_SLOTS" "$0" "$@"
fi
cd "$(git rev-parse --show-toplevel)" || exit 2
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}

usage() {
  cat >&2 <<'EOF'
Aufruf: tools/mp_dedicated_test.sh [fabric|neoforge] [--client-loader fabric|neoforge] [--tree-b <pfad>] [--keep-tree]
                                   [--timeout <minuten>] [--logs <ordner>] [--no-slots]

Mehrspieler mit zwei echten Clients (docs/testing.md, „Dedizierter Server mit zwei echten Clients“): ein dedizierter
Server und Pilot A aus diesem Arbeitsbaum, Pilot B aus einem zweiten Arbeitsbaum desselben Commits. Pilot A fliegt drei
Runden auf einem Kurs mit Geist, Pilot B schaut ihm per Zuschauen-Taste zu (mit Flugbahn-Marker), wird danach Operator am
Gimbal einer Drohne von Pilot A im autonomen Halten und räumt als Server-Operator mit /propwash clear auf; danach
vergleicht das Skript beide Bestenlisten und prüft die drei logs/latest.log auf unerwartete Fehler.

Der Loader vorn ist der des Servers, --client-loader der der beiden Clients (Standard: wie der Server). Gemischt:
  fabric --client-loader neoforge   gleicher Ablauf; dazu passen die Daten jeder Drohne bei beiden Clients zum Server,
                                    und danach betreten beide eine Einzelspieler-Welt (Register zurückgesetzt)
  neoforge --client-loader fabric   Pilot A wird abgelehnt (Meldung „This server runs NeoForge“), der Server läuft weiter,
                                    und Pilot B tritt danach mit dem Loader des Servers bei; die Clients laufen
                                    nacheinander in Slot 1, das Skript belegt nur Slot 4 und Slot 1

  --tree-b <pfad>    zweiter Arbeitsbaum (Standard: <dieser Arbeitsbaum>-mp-b, wird bei Bedarf angelegt)
  --keep-tree        einen vom Skript angelegten zweiten Arbeitsbaum am Ende behalten
  --timeout <min>    so lange auf den geschützten Fall warten (Standard 120), danach Exit 75 ohne Start
  --logs <ordner>    Protokolle dorthin statt in einen neuen Ordner unter /tmp
  --no-slots         ohne Slot-Skript (Rechner ohne Slot-Skript, nur dort)

PROPWASH_MC_SLOTS=<Slot-Skript> ist sonst Pflicht. Geschützter Fall: das Skript startet nur, wenn das Slot-Skript mit
--status Slot 3 und Slot 5 frei, keinen laufenden Server und einen leeren Build-Pool zeigt. Dann fragt es unter der
Wächter-Sperre nacheinander Slot 4, Slot 1 und Slot 2 an; vor jedem Slot muss der Speicher-Wächter (--guard server,
Reserven der schon belegten Slots eingerechnet) zustimmen. Stimmt er für einen nicht zu oder ist einer belegt, gibt das
Skript alle wieder frei und wartet (PROPWASH_MP_POLL Sekunden, Standard 30). Weniger Instanzen gibt es nicht.
PROPWASH_MP_ALLOW=<regex> lässt weitere Log-Zeilen als erwartet gelten. PROPWASH_GRADLEW ersetzt ./gradlew.
Exit 0 bestanden, 1 Befund, 2 Aufruf oder Aufbau, 75 Zeit für den geschützten Fall abgelaufen (nichts gestartet).
EOF
  exit 2
}

LOADER=fabric
CLIENT_LOADER=
TREE_B=
KEEP=0
NO_SLOTS=0
TIMEOUT_MIN=120
OUT=
while [ $# -gt 0 ]; do
  case $1 in
    fabric|neoforge) LOADER=$1 ;;
    --client-loader)
      [ $# -ge 2 ] || usage
      case $2 in fabric|neoforge) CLIENT_LOADER=$2 ;; *) usage ;; esac
      shift ;;
    --tree-b) [ $# -ge 2 ] || usage; TREE_B=$2; shift ;;
    --keep-tree) KEEP=1 ;;
    --timeout) [ $# -ge 2 ] || usage; TIMEOUT_MIN=$2; shift ;;
    --logs) [ $# -ge 2 ] || usage; OUT=$2; shift ;;
    --no-slots) NO_SLOTS=1 ;;
    -h|--help) usage ;;
    *) echo "unbekannt: $1" >&2; usage ;;
  esac
  shift
done
case $TIMEOUT_MIN in ''|*[!0-9]*) echo "--timeout braucht Minuten" >&2; exit 2 ;; esac
CLIENT_LOADER=${CLIENT_LOADER:-$LOADER}
MODE=same
[ "$CLIENT_LOADER" != "$LOADER" ] && MODE=join
[ "$LOADER" = neoforge ] && [ "$CLIENT_LOADER" = fabric ] && MODE=reject

SLOTS=${PROPWASH_MC_SLOTS:-}
if [ -z "$SLOTS" ] && [ $NO_SLOTS = 0 ]; then
  echo "PROPWASH_MC_SLOTS=<Slot-Skript> fehlt (ohne Slot-Skript: --no-slots)" >&2
  exit 2
fi
if [ -n "$SLOTS" ] && [ $NO_SLOTS = 1 ]; then
  echo "--no-slots und PROPWASH_MC_SLOTS schließen sich aus" >&2
  exit 2
fi
if [ -n "$SLOTS" ]; then
  [ -x "$SLOTS" ] || { echo "Slot-Skript $SLOTS nicht ausführbar" >&2; exit 2; }
  W=${MCSLOT_DIR:-$(cd "$(dirname "$SLOTS")" && pwd -P)}
  command -v perl >/dev/null || { echo "perl fehlt (flock)" >&2; exit 2; }
fi
GRADLEW=${PROPWASH_GRADLEW:-./gradlew}
POLL=${PROPWASH_MP_POLL:-30}
TREE_A=$(pwd -P)
COMMIT=$(git rev-parse HEAD)
COMMON=$(git rev-parse --path-format=absolute --git-common-dir)
[ -n "$TREE_B" ] || TREE_B="$(dirname "$TREE_A")/$(basename "$TREE_A")-mp-b"
OUT=${OUT:-$(mktemp -d /tmp/pw-mp-dedicated.XXXXXX)}
mkdir -p "$OUT" || exit 2
OUT=$(cd "$OUT" && pwd -P)
RUNS=$LOADER/runs
CLIENT_RUNS=$CLIENT_LOADER/runs
TREE_B_LOADER=$CLIENT_LOADER
[ $MODE = reject ] && TREE_B_LOADER=$LOADER
SERVER_DIR=$TREE_A/$RUNS/smoke-server
PILOT_LOG=$TREE_A/$CLIENT_RUNS/smoke-client/logs/latest.log
WATCHER_LOG=$TREE_B/$TREE_B_LOADER/runs/smoke-client/logs/latest.log
PILOT_TXT=$TREE_A/$CLIENT_RUNS/smoke-client/jointest/multi-pilot.txt
WATCHER_TXT=$TREE_B/$CLIENT_RUNS/smoke-client/jointest/multi-watcher.txt
REJECT_TXT=$TREE_A/$CLIENT_RUNS/smoke-client/jointest/mixed-reject.txt
CONTROL_TXT=$TREE_B/$TREE_B_LOADER/runs/smoke-client/jointest/mixed-control.txt
REJECT_EXPECT='this server runs neoforge|!joined'
CONTROL_EXPECT='joined'
PILOT_EXPECT='joined|multi role=pilot|multi watcher signal=seen|multi race finished|multi ghost shown|multi leaderboard ok|multi cleared'
WATCHER_EXPECT='joined|multi role=watcher|multi watched pilot|multi watcher marker=drawn|multi watcher racehud=drawn|multi watcher osd=pilot|multi watcher gate=lit|multi operator ok|multi operator released|multi cleared|multi leaderboard ok'

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Arbeitsbaum $TREE_A hat nicht eingecheckte Änderungen; beide Clients müssen denselben Commit haben" >&2
  exit 2
fi

CREATED_B=0
if [ -e "$TREE_B" ]; then
  other=$(git -C "$TREE_B" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)
  if [ "$other" != "$COMMON" ]; then
    echo "$TREE_B ist kein Arbeitsbaum dieses Repositorys" >&2
    exit 2
  fi
  if [ -n "$(git -C "$TREE_B" status --porcelain --untracked-files=no)" ]; then
    echo "Arbeitsbaum $TREE_B hat nicht eingecheckte Änderungen" >&2
    exit 2
  fi
  git -C "$TREE_B" checkout -q --detach "$COMMIT" || exit 2
else
  git worktree add -q --detach "$TREE_B" "$COMMIT" || exit 2
  CREATED_B=1
fi
TREE_B=$(cd "$TREE_B" && pwd -P)
echo "Commit $COMMIT, Pilot A und Server aus $TREE_A, Pilot B aus $TREE_B, Protokolle in $OUT"

HELD=
OWN=
PIDS=
TREE_KEY=
descendants() {
  ps -axo pid=,ppid= | awk -v root="$1" '{ kids[$2] = kids[$2] " " $1 }
    END { n = split(kids[root], q, " "); while (n > 0) { p = q[n]; n--; printf "%s ", p; m = split(kids[p], c, " "); for (i = 1; i <= m; i++) q[++n] = c[i] } }'
}
stop_all() {
  local p all=
  for p in $PIDS; do all="$all $p $(descendants "$p")"; done
  [ -n "${all// /}" ] || return 0
  kill -TERM $all 2>/dev/null
  for _ in $(seq 1 20); do
    kill -0 $all 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL $all 2>/dev/null
}
cleanup() {
  local n d p
  stop_all
  for n in $HELD; do
    d=$W/mc.slot.$n
    p=$(cat "$d/pid" 2>/dev/null)
    case " $$ $OWN " in *" $p "*) rm -rf "$d" ;; esac
  done
  [ -n "$TREE_KEY" ] && [ "$(cut -d' ' -f1 "$W/mc.tree.$TREE_KEY.info" 2>/dev/null)" = "$$" ] && rm -f "$W/mc.tree.$TREE_KEY.info"
  exec 5>&- 6>&- 7>&- 8>&- 9>&-
  HELD=
  if [ $CREATED_B = 1 ] && [ $KEEP = 0 ]; then
    git worktree remove --force "$TREE_B" 2>/dev/null
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

build_tree() {
  local tree=$1 tag=$2 tasks= l
  shift 2
  for l in "$@"; do tasks="$tasks :$l:classes :$l:devtestClasses"; done
  echo "baue $* in $tree"
  if [ -n "$SLOTS" ]; then
    (cd "$tree" && "$SLOTS" "$GRADLEW" -q --no-daemon $tasks) > "$OUT/build-$tag.log" 2>&1
  else
    (cd "$tree" && "$GRADLEW" -q --no-daemon $tasks) > "$OUT/build-$tag.log" 2>&1
  fi
}
if [ "$LOADER" = "$CLIENT_LOADER" ]; then
  build_tree "$TREE_A" a "$LOADER" || { echo "Build in $TREE_A gescheitert, siehe $OUT/build-a.log" >&2; exit 2; }
else
  build_tree "$TREE_A" a "$LOADER" "$CLIENT_LOADER" || { echo "Build in $TREE_A gescheitert, siehe $OUT/build-a.log" >&2; exit 2; }
fi
build_tree "$TREE_B" b "$TREE_B_LOADER" || { echo "Build in $TREE_B gescheitert, siehe $OUT/build-b.log" >&2; exit 2; }

flock_fd() {
  perl -e 'use Fcntl qw(:flock); open(my $h, ">>&=", $ARGV[0]) or exit 2; my $n = $ARGV[1] || 1;
    for my $i (1 .. $n) { exit 0 if flock($h, LOCK_EX | LOCK_NB); exit 2 unless $!{EWOULDBLOCK};
      select(undef, undef, undef, 0.03) if $i < $n } exit 1' "$1" "${2:-1}"
}

precondition() {
  local s
  s=$("$SLOTS" --status 2>/dev/null) || { REASON="Slot-Skript --status gescheitert"; return 1; }
  echo "$s" | grep -qE '^Slot 3 \(.*\): frei$' || { REASON="Slot 3 belegt"; return 1; }
  echo "$s" | grep -qE '^Slot 5 \(.*\): frei$' || { REASON="Slot 5 belegt (zweiter Server)"; return 1; }
  if echo "$s" | grep -E '^Slot [124] \(' | grep -q 'Pool server'; then REASON="ein Server läuft schon"; return 1; fi
  if echo "$s" | grep -qE '^Build-Platz [0-9]+: belegt'; then REASON="Build-Pool nicht leer"; return 1; fi
  return 0
}

release_all() {
  local n d p
  for n in $HELD; do
    d=$W/mc.slot.$n
    p=$(cat "$d/pid" 2>/dev/null)
    [ "$p" = "$$" ] && rm -rf "$d"
  done
  exec 6>&- 7>&- 8>&- 9>&-
  HELD=
  if [ -n "$TREE_KEY" ] && [ "$(cut -d' ' -f1 "$W/mc.tree.$TREE_KEY.info" 2>/dev/null)" = "$$" ]; then
    rm -f "$W/mc.tree.$TREE_KEY.info"
  fi
  TREE_KEY=
}

take() {
  local n=$1 fd=$2 pool=$3 what=$4 d=$W/mc.slot.$1
  eval "exec $fd>>\"$W/mc.slot.$n.lock\"" || return 2
  if ! flock_fd "$fd" 5; then eval "exec $fd>&-"; return 1; fi
  if [ -d "$d" ] && [ ! -e "$d/flock" ]; then eval "exec $fd>&-"; return 1; fi
  rm -rf "$d"; mkdir -p "$d"; : > "$d/flock"
  echo $$ > "$d/pid"; date +%s > "$d/since"; echo "$pool" > "$d/pool"
  echo "$TREE_A $TREE_B" > "$d/tree"; echo "tools/mp_dedicated_test.sh $LOADER: $what" > "$d/cmd"
  HELD="$HELD $n"
}

reserve() {
  local key
  key=$(printf '%s' "$TREE_A" | sed 's/[^A-Za-z0-9._-]/_/g')
  exec 9>>"$W/mc.tree.$key.lock" || return 2
  if ! flock_fd 9 3; then exec 9>&-; REASON="ein Server-Task aus $TREE_A läuft (Arbeitsbaum-Sperre)"; return 1; fi
  TREE_KEY=$key
  echo "$$ $TREE_A" > "$W/mc.tree.$key.info"
  exec 5>>"$W/mc.guard.lock" || { release_all; return 2; }
  if ! flock_fd 5 150; then exec 5>&-; release_all; REASON="Wächter-Sperre besetzt"; return 1; fi
  local spec n fd pool what guard
  SPECS=("4 6 server Server" "1 7 client Pilot-A" "2 8 client Pilot-B")
  [ $MODE = reject ] && SPECS=("4 6 server Server" "1 7 client Clients")
  for spec in "${SPECS[@]}"; do
    set -- $spec
    n=$1 fd=$2 pool=$3 what=$4
    if ! guard=$("$SLOTS" --guard server 2>/dev/null); then
      REASON="Wächter vor Slot $n: $guard"
      exec 5>&-; release_all; return 1
    fi
    if ! take "$n" "$fd" "$pool" "$what"; then
      REASON="Slot $n belegt"
      exec 5>&-; release_all; return 1
    fi
    echo "Slot $n ($what) belegt: $guard"
  done
  exec 5>&-
  return 0
}

if [ -n "$SLOTS" ]; then
  deadline=$(( $(date +%s) + TIMEOUT_MIN * 60 ))
  last=
  while :; do
    REASON=
    if precondition && reserve; then
      break
    fi
    [ "$REASON" != "$last" ] && echo "warte auf den geschützten Fall: $REASON"
    last=$REASON
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "geschützter Fall nach $TIMEOUT_MIN min nicht frei ($REASON), nichts gestartet" >&2
      exit 75
    fi
    sleep "$POLL" &
    wait $!
  done
fi

launch() {
  local slot=$1 fd=$2 tree=$3 log=$4
  shift 4
  (
    for f in 5 6 7 8 9; do
      [ "$f" = "$fd" ] || { [ "$slot" = 4 ] && [ "$f" = 9 ]; } || eval "exec $f>&-"
    done
    cd "$tree" || exit 2
    if [ -n "$SLOTS" ]; then
      export PROPWASH_MC_SLOT=$slot MCSLOT_TREES=$TREE_KEY
      exec sh -c 'echo $$ > "$1"; shift; MCSLOT_HOLDER=$$; export MCSLOT_HOLDER; exec "$@"' mp "$W/mc.slot.$slot/pid" "$@"
    fi
    exec "$@"
  ) > "$log" 2>&1 &
  LAUNCHED=$!
  OWN="$OWN $LAUNCHED"
  PIDS="$PIDS $LAUNCHED"
}

rm -f "$SERVER_DIR/jointest.stop" "$PILOT_TXT" "$WATCHER_TXT" "$REJECT_TXT" "$CONTROL_TXT"
STARTED=$(date +%s)
echo "Server ($LOADER) startet, Clients: $CLIENT_LOADER"
MULTI_SERVER=1
[ $MODE = reject ] && MULTI_SERVER=
CROSS=0
[ $MODE = join ] && CROSS=1
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_MULTI=$MULTI_SERVER PROPWASH_JOINTEST_CROSSLOADER=$CROSS launch 4 6 "$TREE_A" "$OUT/server.log" \
  "$GRADLEW" -q --no-daemon ":$LOADER:runServerSmoke"
SERVER_PID=$LAUNCHED
PORT=
for _ in $(seq 1 240); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  if [ -f "$SERVER_DIR/logs/latest.log" ] && [ "$(stat -f %m "$SERVER_DIR/logs/latest.log")" -ge "$STARTED" ] \
     && grep -q '\[Server-Rauchtest\] gestartet' "$SERVER_DIR/logs/latest.log"; then
    PORT=$(sed -n 's/^server-port=//p' "$SERVER_DIR/server.properties")
    break
  fi
  sleep 5
done
if [ -z "$PORT" ]; then
  echo "Server meldet keinen Start, siehe $OUT/server.log" >&2
  exit 1
fi
SERVER_ALIVE=1
if [ $MODE = reject ]; then
  echo "Server lauscht auf Port $PORT; Pilot A ($CLIENT_LOADER) muss abgelehnt werden"
  PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=mixed-reject PROPWASH_JOINTEST_EXPECT="$REJECT_EXPECT" \
    launch 1 7 "$TREE_A" "$OUT/pilot-a.log" "$GRADLEW" -q --no-daemon ":$CLIENT_LOADER:runClientSmoke" -PpropwashDevName=PwPilotA
  PILOT_PID=$LAUNCHED
  wait "$PILOT_PID"; PILOT_RC=$?
  kill -0 "$SERVER_PID" 2>/dev/null || SERVER_ALIVE=0
  echo "Pilot B ($LOADER) tritt demselben Server bei"
  PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=mixed-control PROPWASH_JOINTEST_EXPECT="$CONTROL_EXPECT" \
    launch 1 7 "$TREE_B" "$OUT/pilot-b.log" "$GRADLEW" -q --no-daemon ":$LOADER:runClientSmoke" -PpropwashDevName=PwPilotB
  WATCHER_PID=$LAUNCHED
  wait "$WATCHER_PID"; WATCHER_RC=$?
else
  echo "Server lauscht auf Port $PORT; Pilot A und Pilot B treten bei"
  PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=multi-pilot PROPWASH_JOINTEST_MULTI=pilot PROPWASH_JOINTEST_CROSSLOADER=$CROSS \
    PROPWASH_JOINTEST_EXPECT="$PILOT_EXPECT" launch 1 7 "$TREE_A" "$OUT/pilot-a.log" \
    "$GRADLEW" -q --no-daemon ":$CLIENT_LOADER:runClientSmoke" -PpropwashDevName=PwPilotA
  PILOT_PID=$LAUNCHED
  sleep 10
  PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=multi-watcher PROPWASH_JOINTEST_MULTI=watcher PROPWASH_JOINTEST_CROSSLOADER=$CROSS \
    PROPWASH_JOINTEST_EXPECT="$WATCHER_EXPECT" launch 2 8 "$TREE_B" "$OUT/pilot-b.log" \
    "$GRADLEW" -q --no-daemon ":$CLIENT_LOADER:runClientSmoke" -PpropwashDevName=PwPilotB
  WATCHER_PID=$LAUNCHED
  wait "$PILOT_PID"; PILOT_RC=$?
  wait "$WATCHER_PID"; WATCHER_RC=$?
fi
touch "$SERVER_DIR/jointest.stop"
for _ in $(seq 1 60); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 2; done
if kill -0 "$SERVER_PID" 2>/dev/null; then
  echo "Server endet nicht nach $SERVER_DIR/jointest.stop, wird beendet" >&2
  PIDS=$SERVER_PID stop_all
fi
wait "$SERVER_PID"; SERVER_RC=$?
PIDS=
echo "Dauer $(( $(date +%s) - STARTED )) s; Exit Server $SERVER_RC, Pilot A $PILOT_RC, Pilot B $WATCHER_RC"

status=0
cp "$SERVER_DIR/logs/latest.log" "$OUT/server-latest.log" 2>/dev/null
cp "$PILOT_LOG" "$OUT/pilot-a-latest.log" 2>/dev/null
cp "$WATCHER_LOG" "$OUT/pilot-b-latest.log" 2>/dev/null
if [ $MODE = reject ]; then
  if [ -f "$REJECT_TXT" ]; then
    echo "--- $(basename "$REJECT_TXT")"
    cat "$REJECT_TXT"
  else
    echo "--- $(basename "$REJECT_TXT") fehlt"; status=1
  fi
  if grep -aq 'PwPilotA .*lost connection: This server runs NeoForge' "$OUT/server-latest.log" 2>/dev/null; then
    echo "Server: Pilot A mit der Meldung für Clients ohne NeoForge getrennt"
  else
    echo "Server meldet die Trennung von Pilot A nicht mit der Meldung für Clients ohne NeoForge"; status=1
  fi
  [ $SERVER_ALIVE = 1 ] && echo "Server läuft nach der Ablehnung weiter" || { echo "Server nach der Ablehnung beendet"; status=1; }
  if [ -f "$CONTROL_TXT" ] && grep -q '^joined' "$CONTROL_TXT"; then
    echo "Pilot B ($LOADER) danach beigetreten"
  else
    echo "Pilot B ($LOADER) nicht beigetreten"; status=1
  fi
else
for f in "$PILOT_TXT" "$WATCHER_TXT"; do
  if [ -f "$f" ]; then
    echo "--- $(basename "$f")"
    grep -E '^(joined|multi )' "$f"
  else
    echo "--- $(basename "$f") fehlt"; status=1
  fi
done
grep -a '\[Beitrittstest\] Mehrspieler' "$OUT/server-latest.log" 2>/dev/null | sed 's/^.*\[Beitrittstest\]/Server:/' | tail -n 12
if grep -aq 'Mehrspieler: Drohne .* entfernt (/propwash clear durch PwPilotB)' "$OUT/server-latest.log" 2>/dev/null; then
  echo "Server: Drohne von Pilot A durch /propwash clear von Pilot B entfernt"
else
  echo "Server meldet kein /propwash clear durch Pilot B"; status=1
fi
if grep -aq 'Mehrspieler: PwPilotB wird Operator am Gimbal der Drohne .* im autonomen Halten (gebunden' "$OUT/server-latest.log" 2>/dev/null; then
  echo "Server: Pilot B ist Operator am Gimbal einer Drohne im autonomen Halten"
else
  echo "Server meldet Pilot B nicht als Operator am Gimbal"; status=1
fi
gimbal=$(grep -a 'Mehrspieler: Gimbal-Drohne .* entfernt (/propwash clear durch PwPilotB), autonomer Flug beendet: [A-Z_]*, Operator-Bindungen 0,' \
  "$OUT/server-latest.log" 2>/dev/null | tail -n 1)
if [ -n "$gimbal" ]; then
  echo "Server: ${gimbal#*Mehrspieler: }"
else
  echo "Server meldet die Gimbal-Drohne nicht als entfernt (autonomer Flug beendet, keine Operator-Bindung)"; status=1
fi
grep -h '^multi operator drone=' "$WATCHER_TXT" 2>/dev/null | sed 's/^/Zuschauer: /'
grep -hE '^multi watcher (racehud|osd|sticks|gate|shot)=' "$WATCHER_TXT" 2>/dev/null | sed 's/^/Zuschauer: /'

BOARD_A=$(grep '^multi board=' "$PILOT_TXT" 2>/dev/null)
BOARD_B=$(grep '^multi board=' "$WATCHER_TXT" 2>/dev/null)
if [ -n "$BOARD_A" ] && [ "$BOARD_A" = "$BOARD_B" ] && [ "$BOARD_A" != "multi board=none" ]; then
  echo "Bestenliste bei beiden gleich: ${BOARD_A#multi board=}"
else
  echo "Bestenlisten verschieden: A '${BOARD_A#multi board=}', B '${BOARD_B#multi board=}'"; status=1
fi
if [ $MODE = join ]; then
  for name in pilot-a pilot-b; do
    if grep -aq 'Register an den Fabric-Server angeglichen' "$OUT/$name-latest.log" 2>/dev/null; then
      echo "$name: $(grep -a 'Register an den Fabric-Server angeglichen' "$OUT/$name-latest.log" | tail -n 1 | sed 's/^.*Propwash: //')"
    else
      echo "$name: Registerabgleich mit dem Fabric-Server fehlt im Log"; status=1
    fi
    if grep -aq 'PASS Beitrittstest Einzelspieler nach dem Fabric-Server' "$OUT/$name-latest.log" 2>/dev/null; then
      echo "$name: danach Einzelspieler mit Drohne, Register zurückgesetzt"
    else
      echo "$name: Einzelspieler danach nicht bestanden"; status=1
    fi
  done
  for f in "$PILOT_TXT" "$WATCHER_TXT"; do
    seen=0
    while read -r _ _ id digest; do
      seen=$((seen + 1))
      if ! grep -aq "gemischt: Drohne $id Daten $digest " "$OUT/server-latest.log" 2>/dev/null; then
        echo "$(basename "$f"): Drohne $id mit Daten $digest, der Server kennt sie so nicht"; status=1
      fi
    done < <(grep '^crossloader drone ' "$f" 2>/dev/null)
    if [ $seen -gt 0 ]; then
      echo "$(basename "$f"): $seen Drohnen mit denselben Daten wie am Server (Bauplan, Lack, Zubehör, Tune)"
    else
      echo "$(basename "$f"): keine Drohne gesehen"; status=1
    fi
  done
fi
fi
[ $SERVER_RC -eq 0 ] && [ $PILOT_RC -eq 0 ] && [ $WATCHER_RC -eq 0 ] || status=1

ALLOW='Failed to (fetch|retrieve) (user properties|profile)|InvalidCredentialsException|[Ss]tatus(=|: )401|Couldn.t (load|fetch) (profile|skin)|Unable to (load|fetch) (skin|profile)|realms|Realms|Telemetry|telemetry|YggdrasilUserApiService|minecraftservices|sessionserver|api\.mojang\.com|Narrator|narrator|OpenAL|Unable to start the LAN|PwPilot[AB] lost connection|Can.t keep up|Failed to deliver packet, sending fallback clientbound/minecraft:system_chat|StacklessClosedChannelException|Netty Local IO #[0-9]+/ERROR\] \[net\.minecraft\.network\.Connection/\]: Exception caught in connection|^java\.nio\.channels\.ClosedChannelException$'
[ -n "${PROPWASH_MP_ALLOW:-}" ] && ALLOW="$ALLOW|$PROPWASH_MP_ALLOW"
for name in server-latest pilot-a-latest pilot-b-latest; do
  f=$OUT/$name.log
  if [ ! -s "$f" ]; then
    echo "$name.log fehlt"; status=1; continue
  fi
  bad=$(grep -aE '/(ERROR|FATAL)\]|Exception|Caused by' "$f" | grep -avE '^[[:space:]]+(at |\.\.\. [0-9]+ more)' | grep -avE "$ALLOW")
  if [ -n "$bad" ]; then
    echo "$name.log: $(printf '%s\n' "$bad" | wc -l | tr -d ' ') unerwartete Fehlerzeilen, die ersten:"
    printf '%s\n' "$bad" | head -n 8
    status=1
  else
    echo "$name.log ohne unerwartete Fehler"
  fi
done
[ $status -eq 0 ] && echo "MP-DEDIZIERT PASS (Server $LOADER, Clients $CLIENT_LOADER)" || echo "MP-DEDIZIERT FAIL (Server $LOADER, Clients $CLIENT_LOADER), Protokolle in $OUT"
exit $status
