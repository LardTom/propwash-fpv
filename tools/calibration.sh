#!/bin/bash
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
COMMON=$(cd "$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")" && pwd)
ADDON=${1:-$COMMON/../propwash-justmoreparts}
OUT=${PROPWASH_CALIBRATION_OUT:-$ROOT/build/calibration}
RESOURCES="$ADDON/common/src/main/resources"
if [ ! -d "$RESOURCES/data/justmoreparts/propwash_parts" ]; then
  echo "Addon-Daten nicht gefunden: $RESOURCES – nur eingebaute Presets" >&2
  RESOURCES=""
fi
mkdir -p "$OUT"
cd "$ROOT" || exit 2
PROPWASH_CALIBRATION_ADDON="$RESOURCES" PROPWASH_CALIBRATION_OUT="$OUT" tools/fastcheck.sh test \
  --select-class de.lardtom.propwash.sim.CalibrationHarnessTest --select-class de.lardtom.propwash.sim.FlightTimeOrderTest \
  --select-class de.lardtom.propwash.sim.MotionFingerprintTest --select-class de.lardtom.propwash.sim.FallCharacterizationTest \
  --select-class de.lardtom.propwash.sim.ThrottleDropCharacterizationTest --select-class de.lardtom.propwash.sim.BatterySagTest \
  | grep -E 'tests (successful|failed|aborted)|FASTCHECK'
status=${PIPESTATUS[0]}
cat "$OUT/calibration.txt"
cat "$OUT/battery_sag.txt"
exit "$status"
