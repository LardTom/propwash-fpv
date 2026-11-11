#!/bin/bash
set -u
LOADER=${1:?Loader fabric oder neoforge}
shift
JARS=("$@")
if [ ${#JARS[@]} -eq 0 ]; then
  echo "Aufruf: tools/remote_cov_addoncycle_test.sh <loader> <jar> [<jar> …]   (Mob Defense und Kamera-Add-on)" >&2
  exit 2
fi
for jar in "${JARS[@]}"; do
  [ -f "$jar" ] || { echo "Jar fehlt: $jar" >&2; exit 2; }
done
SLOTS=${PROPWASH_MC_SLOTS:-}
ROOT=$(pwd)
OUT=${COV_ADDONCYCLE_OUT:-$ROOT/build/cov-addoncycle}
RUN_DIR=$ROOT/$LOADER/runs/selftest-client
MODS=$RUN_DIR/mods
PREFIX='platzhalter: Abdeckung cov-combo – Start'
mkdir -p "$OUT" "$MODS"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}

run() {
  if [ -n "$SLOTS" ]; then
    "$SLOTS" "$@"
  else
    "$@"
  fi
}

placed=()
place() {
  for jar in "${JARS[@]}"; do
    cp "$jar" "$MODS/"
    placed+=("$MODS/$(basename "$jar")")
  done
}

remove() {
  for jar in "${placed[@]+"${placed[@]}"}"; do
    rm -f "$jar"
  done
  placed=()
}
trap remove EXIT

start() {
  local number=$1
  local log=$OUT/$LOADER-start$number.out
  PROPWASH_DEV_EXAMPLE=off run ./gradlew ":$LOADER:runClientSelftest" "-PpropwashSelftestOnly=$PREFIX $number" --console=plain > "$log" 2>&1
  local rc=$?
  local result
  result=$(grep -E "^Selbsttest \((Fabric|NeoForge)\)" "$log" | tail -1)
  echo "$LOADER Start $number: Exit $rc ${result:-ohne Ergebnis}" | tee -a "$OUT/$LOADER-summary.txt"
  grep -E "\[Selbsttest\] (PASS|FAIL) $PREFIX" "$log" | sed -E 's/^.*\[Selbsttest\] //' >> "$OUT/$LOADER-summary.txt"
  return $rc
}

rm -f "$OUT/$LOADER-summary.txt"
place
start 1 || exit 1
remove
start 2 || exit 1
place
start 3 || exit 1
cp "$RUN_DIR"/cov-combo/addoncycle/start*.json "$OUT/" 2>/dev/null
echo "$LOADER: drei Starts bestanden, Stände in $OUT" | tee -a "$OUT/$LOADER-summary.txt"
