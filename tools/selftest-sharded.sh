#!/bin/bash
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}
HERE=$(pwd)
GRADLEW=${PROPWASH_GRADLEW:-./gradlew}

usage() {
  cat >&2 <<'EOF'
Aufruf: tools/selftest-sharded.sh <fabric|neoforge|all> [--screenshots] [--shards <n|Anker,Anker>] [--plan]
                                  [--allow-unclean] [--retries <n>] [--clean]

Lässt den Selbsttest in zusammenhängenden Teilen gleichzeitig laufen (docs/testing.md, „Selbsttest in Teilen“):
Schrittliste holen, Teile planen, jeden Teil mit eigener Run-Kennung über das Slot-Skript starten, am Ende prüfen,
dass jeder Schritt der Liste in genau einem Teil bestanden hat.

  --screenshots   mit Bildschirmfoto-Schritten (Fabric)
  --shards <n>    Zahl der Teile (Standard 2), schnellste Aufteilung nach den Laufzeiten der letzten Läufe;
                  oder Namensanfänge, vor denen geschnitten wird, z. B. --shards guide:,bildschirmfotos
  --plan          nur planen, mit der vorhandenen Schrittliste, ohne Minecraft
  --retries <n>   Teile, die das Slot-Skript wegen Speichermangels abbricht (Exit 75), erneut starten (Standard 2)
  --clean         Run-Verzeichnisse der Teile (runs/*-shard-*) löschen

PROPWASH_MC_LAUNCH=<Slot-Skript> ist Pflicht: jeder Teil wartet dort auf einen freien Client-Slot (mit
MCSLOT_GUARDED_CLIENT=1 auch auf Slot 3; bricht das Slot-Skript ihn dort ab, Exit 75, startet er neu).
PROPWASH_SHARD_LOGS=<Ordner> statt runs/selftest-sharded/<Zeit>; PROPWASH_GRADLEW ersetzt ./gradlew (Tests).
EOF
  exit 2
}

if [ "${1:-}" = --run-shard ]; then
  L=$2 TAG=$3 TASK=$4
  shift 4
  OUT=${PROPWASH_SHARD_OUT:?}
  date +%s > "$OUT/$L-$TAG.slot"
  G=
  trap '[ -n "$G" ] && { kill -TERM $G 2>/dev/null; wait $G; }; exit 143' TERM INT HUP
  # Start-Sperre: flock(2) auf Deskriptor 9. Die Sperre gehört der offenen Datei und endet mit diesem Skript (auch bei
  # KILL), nie mit einem anderen Teil; nichts wird gelöscht. Gradle erbt den Deskriptor nicht (9>&-), sonst hielte es
  # die Sperre bis zum Ende des Teils.
  exec 9>>"$OUT/start.lock"
  while :; do
    python3 -c 'import errno, fcntl, sys
try:
    fcntl.flock(9, fcntl.LOCK_EX | fcntl.LOCK_NB)
except OSError as e:
    sys.exit(1 if e.errno in (errno.EWOULDBLOCK, errno.EAGAIN) else 2)'
    case $? in
      0) break ;;
      1) sleep 2 ;;
      *) echo "Start-Sperre $OUT/start.lock: flock gescheitert" >&2; exit 2 ;;
    esac
  done
  log=$L/runs/selftest-client-$TAG/logs/latest.log
  start=$(date +%s)
  $GRADLEW --no-daemon "$TASK" "-PpropwashRunTag=$TAG" ${1+"$@"} 9>&- &
  G=$!
  waited=0
  while kill -0 $G 2>/dev/null && [ $waited -lt 900 ]; do
    if [ -f "$log" ] && [ "$(date -r "$log" +%s)" -ge "$start" ] && grep -q '\[Selbsttest\]' "$log"; then
      break
    fi
    sleep 2
    waited=$((waited + 2))
  done
  exec 9>&-
  wait $G
  exit $?
fi

LOADERS=() SHOTS=() SPEC=2 PLAN_ONLY=0 CLEAN=0 RETRIES=2 UNCLEAN=()
while [ $# -gt 0 ]; do
  case $1 in
    fabric|neoforge) LOADERS+=("$1") ;;
    all) LOADERS=(fabric neoforge) ;;
    --screenshots) SHOTS=(-PpropwashScreenshots=true) ;;
    --shards) SPEC=${2:?}; shift ;;
    --plan) PLAN_ONLY=1 ;;
    --allow-unclean) UNCLEAN=(--allow-unclean) ;;
    --retries) RETRIES=${2:?}; shift ;;
    --clean) CLEAN=1 ;;
    *) usage ;;
  esac
  shift
done
[ ${#LOADERS[@]} -gt 0 ] || usage

if [ $CLEAN -eq 1 ]; then
  for L in "${LOADERS[@]}"; do
    for d in "$L"/runs/*-shard-*; do
      [ -d "$d" ] && rm -rf "$d" && echo "gelöscht: $d"
    done
  done
  exit 0
fi

timing_logs() {
  ls -t "$1"/runs/selftest-client*/logs/latest.log "$1"/runs/selftest-client*/logs/*.log.gz 2>/dev/null | head -n 40 |
    while IFS= read -r f; do printf '%s\n%s\n' --timing "$f"; done
}

plan_loader() {
  local L=$1 list=$2
  local args=()
  while IFS= read -r line; do args+=("$line"); done < <(timing_logs "$L")
  python3 tools/selftest_shards.py plan --list "$list" --shards "$SPEC" ${UNCLEAN[@]+"${UNCLEAN[@]}"} \
    ${args[@]+"${args[@]}"} --out "$OUT/$L-plan.json"
}

if [ $PLAN_ONLY -eq 1 ]; then
  OUT=$(mktemp -d "${TMPDIR:-/tmp}/pw-shard-plan.XXXXXX")
  status=0
  for L in "${LOADERS[@]}"; do
    list=$L/runs/selftest-client/propwash-selftest-steps.md
    [ -f "$list" ] || { echo "$L: keine Schrittliste in $list – einmal mit -PpropwashSelftestList=true holen" >&2; status=1; continue; }
    echo "Plan aus $list ($(date -r "$list" '+%d.%m. %H:%M'))"
    plan_loader "$L" "$list" || status=1
  done
  rm -rf "$OUT"
  exit $status
fi

LAUNCH=${PROPWASH_MC_LAUNCH:-}
[ -n "$LAUNCH" ] || { echo "PROPWASH_MC_LAUNCH fehlt: ohne Slot-Skript liefen alle Teile ungebremst nebeneinander" >&2; exit 2; }
OUT=${PROPWASH_SHARD_LOGS:-runs/selftest-sharded/$(date +%Y%m%d-%H%M%S)}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
export PROPWASH_SHARD_OUT=$OUT
for L in "${LOADERS[@]}"; do rm -f "$OUT/$L"-shard-*.rc "$OUT/$L"-shard-*.start "$OUT/$L"-shard-*.end "$OUT/$L"-shard-*.slot; done
T0=$(date +%s)
echo "Selbsttest in Teilen: ${LOADERS[*]} ${SHOTS[*]:-}, Teile $SPEC, Protokolle in $OUT"

MON=
if grep -q -- '--sample-header' "${LAUNCH%% *}" 2>/dev/null; then
  ( $LAUNCH --sample-header; while :; do $LAUNCH --sample; sleep 30; done ) > "$OUT/memory.csv" 2>/dev/null &
  MON=$!
fi
JOBS=()
stop_all() {
  for p in ${JOBS[@]+"${JOBS[@]}"}; do pkill -TERM -P "$p" 2>/dev/null; kill -TERM "$p" 2>/dev/null; done
  [ -n "$MON" ] && kill "$MON" 2>/dev/null
}
trap 'stop_all; exit 130' INT TERM HUP

for L in "${LOADERS[@]}"; do
  echo "$L: Schrittliste holen …"
  if ! $LAUNCH $GRADLEW ":$L:runClientSelftest" -PpropwashRunTag=shard-list -PpropwashSelftestList=true ${SHOTS[@]+"${SHOTS[@]}"} \
      > "$OUT/$L-list.log" 2>&1; then
    echo "$L: Schrittliste gescheitert, siehe $OUT/$L-list.log" >&2
    stop_all
    exit 1
  fi
  cp "$L/runs/selftest-client-shard-list/propwash-selftest-steps.md" "$OUT/$L-steps.md"
  plan_loader "$L" "$OUT/$L-steps.md" | tee "$OUT/$L-plan.txt"
  [ "${PIPESTATUS[0]}" -eq 0 ] || { stop_all; exit 1; }
done

QUEUE=$(for L in "${LOADERS[@]}"; do
  python3 - "$OUT/$L-plan.json" "$L" <<'EOF'
import json, sys
for s in json.load(open(sys.argv[1], encoding="utf-8"))["shards"]:
    print(f"{s['estimate_s']}\t{sys.argv[2]}\t{s['tag']}\t{s['filter']}")
EOF
done | sort -rn)

run_shard() {
  local L=$1 TAG=$2 FILTER=$3 attempt=0 rc
  local log=$OUT/$L-$TAG.log rundir=$L/runs/selftest-client-$TAG
  while :; do
    attempt=$((attempt + 1))
    # Ergebnis und Schrittliste eines früheren Laufs dürfen nie als dieser Teil zählen (die Prüfung verlangt zudem
    # Gradle-Exit 0 und Dateien, die jünger sind als dieser Start)
    rm -f "$rundir/propwash-selftest-result.txt" "$rundir/propwash-selftest-steps.md" "$OUT/$L-$TAG.rc"
    date +%s > "$OUT/$L-$TAG.start"
    MCSLOT_GUARDED_CLIENT=1 PROPWASH_SELFTEST_ONLY=$FILTER $LAUNCH "$HERE/tools/selftest-sharded.sh" --run-shard "$L" "$TAG" \
      ":$L:runClientSelftest" ${SHOTS[@]+"${SHOTS[@]}"} >> "$log" 2>&1
    rc=$?
    date +%s > "$OUT/$L-$TAG.end"
    if [ $rc -eq 75 ] && [ $attempt -le "$RETRIES" ]; then
      echo "$L $TAG: vom Slot-Skript wegen Speichermangels abgebrochen, Versuch $((attempt + 1))"
      continue
    fi
    echo $rc > "$OUT/$L-$TAG.rc"
    local t0 t1 ts
    t0=$(cat "$OUT/$L-$TAG.start") t1=$(cat "$OUT/$L-$TAG.end")
    ts=$(cat "$OUT/$L-$TAG.slot" 2>/dev/null || echo "$t0")
    echo "$L $TAG: Gradle-Exit $rc nach $(( (t1 - ts) / 60 )) min im Slot, davor $(( (ts - t0) / 60 )) min auf einen Slot gewartet"
    return $rc
  done
}

while IFS=$'\t' read -r est L TAG FILTER; do
  [ -n "$TAG" ] || continue
  echo "$L $TAG startet (≈ $((est / 60)) min): $FILTER"
  run_shard "$L" "$TAG" "$FILTER" &
  JOBS+=($!)
  sleep 1
done <<< "$QUEUE"
for p in ${JOBS[@]+"${JOBS[@]}"}; do wait "$p"; done
[ -n "$MON" ] && kill "$MON" 2>/dev/null

status=0
for L in "${LOADERS[@]}"; do
  args=()
  for TAG in $(python3 -c 'import json,sys; print(" ".join(s["tag"] for s in json.load(open(sys.argv[1]))["shards"]))' "$OUT/$L-plan.json"); do
    args+=(--shard "$TAG=$L/runs/selftest-client-$TAG")
    [ -f "$OUT/$L-$TAG.rc" ] && args+=(--exit "$TAG=$(cat "$OUT/$L-$TAG.rc")")
    [ -f "$OUT/$L-$TAG.start" ] && args+=(--started "$TAG=$(cat "$OUT/$L-$TAG.start")")
  done
  python3 tools/selftest_shards.py check --plan "$OUT/$L-plan.json" --list "$OUT/$L-steps.md" "${args[@]}" \
    | tee "$OUT/$L-check.txt"
  [ "${PIPESTATUS[0]}" -eq 0 ] || status=1
done

if [ -s "$OUT/memory.csv" ]; then
  awk -F, 'NR > 1 {
      n++; if (min == "" || $2 < min) { min = $2; at = $1 } if ($9 > press) press = $9
      if ($8 > swap) swap = $8; if ($7 > comp) comp = $7; h = ($10 == "-") ? 0 : length($10); if (h > held) held = h
      sum = 0; for (i = 11; i <= 15; i++) { sum += $i; if ($i > peak[i]) peak[i] = $i } if (sum > top) top = sum }
    END { if (n) printf "Speicher (%d Messungen): frei mindestens %d MB (%s), Druck höchstens %d, Swap bis %d MB, Kompressor bis %d MB, bis zu %d Slots, Slots zusammen bis %d MB (Slot 1–5: %d/%d/%d/%d/%d MB)\n",
      n, min, at, press, swap, comp, held, top, peak[11], peak[12], peak[13], peak[14], peak[15] }' "$OUT/memory.csv" \
    | tee "$OUT/memory.txt"
fi
echo "Selbsttest in Teilen: $([ $status -eq 0 ] && echo PASS || echo FAIL) nach $(( ($(date +%s) - T0) / 60 )) min, Protokolle in $OUT"
exit $status
