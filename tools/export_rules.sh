#!/bin/bash
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/build/web-export/gamerules.json}
cd "$ROOT" || exit 2
PROPWASH_RULES_OUT="$OUT" tools/fastcheck.sh test --select-class de.lardtom.propwash.server.rules.WorldRulesExportTest \
  | grep -E 'tests (successful|failed|aborted)|FASTCHECK'
status=${PIPESTATUS[0]}
[ "$status" -eq 0 ] && echo "Welt-Regeln exportiert: $OUT"
exit "$status"
