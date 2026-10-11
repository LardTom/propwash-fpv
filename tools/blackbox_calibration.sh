#!/bin/bash
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
COMMON=$(cd "$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")" && pwd)
CACHE=${PROPWASH_BBCAL_CACHE:-$COMMON/.cache/bbcal}
OUT=${PROPWASH_BBCAL_OUT:-$ROOT/build/bbcal}
SOURCES=${PROPWASH_BBCAL_SOURCES:-$ROOT/tools/bbcal/sources.json}
ADDON=${PROPWASH_BBCAL_ADDON:-$COMMON/../propwash-justmoreparts}
DECODER=$CACHE/src/blackbox-tools/obj/blackbox_decode
MODE=${1:-all}
shift 2>/dev/null

usage() {
  cat <<EOF
Aufruf: tools/blackbox_calibration.sh [all|fetch|roundtrip|compare|scan <ordner>]
  fetch      öffentliche Logs aus tools/bbcal/sources.json laden und dekodieren (Cache: $CACHE)
  roundtrip  Decoder-Prüfung mit Logs aus dem Blackbox-Schreiber von Propwash
  compare    echte Logs gegen das Flugmodell, Bericht nach $OUT/report.md
  scan       alle Logs (.bbl/.bfl/.txt) in einem Ordner dekodieren und auf brauchbare Abschnitte prüfen
  all        fetch, roundtrip und compare
EOF
}

decoder() {
  for tool in git make cc python3; do
    command -v "$tool" >/dev/null || { echo "$tool fehlt" >&2; exit 2; }
  done
  if [ ! -x "$DECODER" ]; then
    mkdir -p "$CACHE/src"
    [ -d "$CACHE/src/blackbox-tools" ] || git clone -q --depth 1 https://github.com/betaflight/blackbox-tools.git "$CACHE/src/blackbox-tools" || exit 2
    make -C "$CACHE/src/blackbox-tools" obj/blackbox_decode >/dev/null || exit 2
  fi
}

addon_env() {
  RES="$ADDON/common/src/main/resources"
  if [ -d "$RES/data/justmoreparts/propwash_parts" ]; then
    echo "$RES"
  else
    echo "Just-More-Parts-Daten nicht gefunden ($ADDON) – Logs mit Add-on-Teilen werden übersprungen" >&2
    echo ""
  fi
}

fetch() {
  decoder
  python3 -I "$ROOT/tools/bbcal/fetch.py" --sources "$SOURCES" --cache "$CACHE" --decoder "$DECODER" "$@"
}

roundtrip() {
  decoder
  cd "$ROOT" || exit 2
  PROPWASH_BBCAL_DECODER="$DECODER" tools/fastcheck.sh test --select-class de.lardtom.propwash.blackbox.CalibrationRoundTripTest \
    | grep -E 'tests (successful|failed|aborted)|FASTCHECK|Rundreise'
  return "${PIPESTATUS[0]}"
}

compare() {
  mkdir -p "$OUT"
  cd "$ROOT" || exit 2
  PROPWASH_BBCAL_DECODED="$CACHE/decoded" PROPWASH_BBCAL_SOURCES="$SOURCES" PROPWASH_BBCAL_OUT="$OUT" PROPWASH_CALIBRATION_ADDON="$(addon_env)" \
    tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.RealLogCalibrationTest > "$OUT/compare.txt" 2>&1
  status=$?
  grep -E '^[a-z0-9_]+ +(verwendet|verworfen)|tests (successful|failed|aborted)|FASTCHECK' "$OUT/compare.txt"
  echo "Bericht: $OUT/report.md"
  return $status
}

scan() {
  decoder
  dir=${1:?Ordner mit Logs angeben}
  work="$OUT/scan"
  rm -rf "$work"
  mkdir -p "$work"
  find "$dir" -type f \( -iname '*.bbl' -o -iname '*.bfl' -o -iname '*.txt' \) | while read -r f; do
    head -c 40 "$f" | grep -q "H Product:Blackbox" || continue
    name=$(basename "$f" | tr -c 'A-Za-z0-9._-\n' '_')
    mkdir -p "$work/$name"
    cp "$f" "$work/$name/log.bbl"
    (cd "$work/$name" && "$DECODER" --unit-vbat raw --unit-amperage raw --unit-flags raw --merge-gps --save-headers log.bbl >/dev/null 2>&1)
    rm -f "$work/$name/log.bbl" "$work/$name"/*.event "$work/$name"/*.gps.*
  done
  cd "$ROOT" || exit 2
  PROPWASH_BBCAL_SCAN="$work" tools/fastcheck.sh test --select-class de.lardtom.propwash.calibration.LogScanTest | grep -E 'tests (successful|failed)'
  echo "Übersicht: $work/scan.tsv"
}

case "$MODE" in
  fetch) fetch "$@" ;;
  roundtrip) roundtrip ;;
  compare) compare ;;
  scan) scan "$@" ;;
  all) fetch && roundtrip && compare ;;
  *) usage; exit 2 ;;
esac
