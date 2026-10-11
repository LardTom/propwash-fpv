#!/bin/bash
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${PROPWASH_BLACKBOX_WORK:-$ROOT/build/blackbox-check}
SRC=$WORK/src
NODE=$WORK/node
FIXTURE=$WORK/fixture
OUT=$WORK/out
for tool in git make cc node npm python3; do
  command -v "$tool" >/dev/null || { echo "$tool fehlt" >&2; exit 2; }
done
mkdir -p "$SRC" "$NODE" "$OUT"
[ -d "$SRC/blackbox-tools" ] || git clone --depth 1 https://github.com/betaflight/blackbox-tools.git "$SRC/blackbox-tools" || exit 2
EXPLORER_TAG=${PROPWASH_EXPLORER_TAG:-2025.12.1}
VIEWER=$SRC/blackbox-log-viewer-$EXPLORER_TAG
[ -d "$VIEWER" ] || git clone --depth 1 --branch "$EXPLORER_TAG" https://github.com/betaflight/blackbox-log-viewer.git "$VIEWER" || exit 2
DECODE=$SRC/blackbox-tools/obj/blackbox_decode
[ -x "$DECODE" ] || make -C "$SRC/blackbox-tools" obj/blackbox_decode >/dev/null || exit 2
if [ ! -d "$NODE/node_modules/esbuild" ] || [ ! -d "$NODE/node_modules/semver" ]; then
  (cd "$NODE" && { [ -f package.json ] || npm init -y >/dev/null; } && npm install --no-audit --no-fund esbuild semver >/dev/null) || exit 2
fi
rm -rf "$FIXTURE" "$OUT"
mkdir -p "$FIXTURE" "$OUT"
echo "Fixtures aus dem Flugmodell schreiben …"
PROPWASH_BLACKBOX_FIXTURE=$FIXTURE "$ROOT/tools/fastcheck.sh" test --select-class de.lardtom.propwash.blackbox.BlackboxFixtureTest \
  | grep -E "tests (successful|failed)" || exit 1
status=0
cp "$FIXTURE"/*.bbl "$OUT"/
for log in "$OUT"/*.bbl; do
  name=$(basename "$log" .bbl)
  (cd "$OUT" && "$DECODE" "$name.bbl") > "$OUT/$name.decode.txt" 2>&1 || { echo "blackbox_decode $name: Abbruch"; status=1; }
  if grep -qiE "corrupt|desync|invalid|unsupported|error" "$OUT/$name.decode.txt"; then
    echo "blackbox_decode $name meldet Probleme:"; grep -iE "corrupt|desync|invalid|unsupported|error" "$OUT/$name.decode.txt"; status=1
  fi
  frames=$(grep -E "^Frames " "$OUT/$name.decode.txt" | awk '{print $2}' | tr '\n' ' ')
  echo "blackbox_decode $name: Frames je Log: $frames"
  if [ -f "$FIXTURE/$name.frames" ] && [ "$(tr '\n' ' ' < "$FIXTURE/$name.frames")" != "$frames" ]; then
    echo "blackbox_decode $name: erwartet $(tr '\n' ' ' < "$FIXTURE/$name.frames")"; status=1
  fi
  if [ -f "$FIXTURE/$name.truth.csv" ]; then
    python3 -I "$ROOT/tools/blackbox/compare.py" "$OUT/$name.01.csv" "$FIXTURE/$name.truth.csv" "blackbox_decode $name" || status=1
  fi
done
cp "$FIXTURE"/*.frames "$OUT"/ 2>/dev/null
echo "Blackbox Explorer $EXPLORER_TAG:"
node "$ROOT/tools/blackbox/explorer_check.mjs" "$VIEWER/src" "$NODE/node_modules" "$OUT/explorer" "$OUT"/*.bbl \
  2> "$OUT/explorer.stderr" | grep -v "^Ignoring unsupported header"
explorer=${PIPESTATUS[0]}
if [ "$explorer" -ne 0 ]; then
  echo "Blackbox Explorer: Exit $explorer"; grep -E "Error|error" "$OUT/explorer.stderr" | head -5; status=1
fi
for truth in "$FIXTURE"/*.truth.csv; do
  name=$(basename "$truth" .truth.csv)
  csv=$OUT/explorer/$name.01.explorer.csv
  if [ ! -f "$csv" ]; then
    echo "Blackbox Explorer $name: keine Ausgabe"; status=1; continue
  fi
  python3 -I "$ROOT/tools/blackbox/compare.py" --raw-battery "$csv" "$truth" "Blackbox Explorer $name" || status=1
done
[ $status -eq 0 ] && echo "BLACKBOX-PRÜFUNG OK" || echo "BLACKBOX-PRÜFUNG FEHLER"
exit $status
