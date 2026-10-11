#!/bin/bash
set -u
MAIN="$(cd "$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")" && pwd)"
JDK=/opt/homebrew/opt/openjdk@25/bin
TARGET=${1:-common}
ROOT=$(pwd)
OUT=$(mktemp -d /tmp/pw-fastcheck.XXXXXX)
trap 'rm -rf "$OUT"' EXIT

CP=$MAIN/.classpath
if [ -f "$ROOT/.classpath/common.txt" ]; then
  CP=$ROOT/.classpath
fi
if [ ! -f "$CP/common.txt" ]; then
  echo "Classpath fehlt: im Hauptbaum einmal 'JAVA_HOME=$JDK/.. ./gradlew writeClasspath' ausführen" >&2
  exit 2
fi

sources() {
  find "$ROOT/$1/src/main/java" "$ROOT/$1/src/devtest/java" -name '*.java' 2>/dev/null
}

compile() {
  local name=$1 cp=$2
  shift 2
  mkdir -p "$OUT/$name"
  "$JDK/javac" -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 400 -d "$OUT/$name" -cp "$cp" "$@" 2>&1
  return ${PIPESTATUS[0]}
}

status=0
case "$TARGET" in
  common)
    compile common "$(cat "$CP/common.txt")" $(sources common) || status=1 ;;
  fabric)
    compile fabric "$(cat "$CP/fabric.txt")" $(sources common) $(sources fabric) || status=1 ;;
  neoforge)
    compile neoforge "$(cat "$CP/neoforge.txt")" $(sources common) $(sources neoforge) || status=1 ;;
  all)
    for t in common fabric neoforge; do "$0" "$t" || status=1; done ;;
  test)
    compile common "$(cat "$CP/common.txt")" $(sources common) || exit 1
    tests=$(find "$ROOT/common/src/test/java" -name '*.java' 2>/dev/null)
    [ -z "$tests" ] && { echo "keine Tests"; exit 0; }
    cp_test="$OUT/common:$ROOT/common/src/main/resources:$(cat "$CP/common.txt"):$(cat "$CP/common-test.txt")"
    compile tests "$cp_test" $tests || exit 1
    shift
    junit() {
      "$JDK/java" -jar "$MAIN/.classpath/junit-console.jar" execute --disable-banner --details=summary \
        -cp "$OUT/tests:$cp_test" "$@" 2>&1 | tail -40
      return ${PIPESTATUS[0]}
    }
    if [ $# -gt 0 ]; then
      junit "$@" || status=1
    elif [ -n "${PROPWASH_CALIBRATION_ADDON:-}" ]; then
      echo "Mit Add-on-Daten in zwei Läufen: Suite ohne Add-on-Daten, dann die Tests mit Tag addon-daten"
      (unset PROPWASH_CALIBRATION_ADDON; junit --scan-class-path --exclude-tag addon-daten) || status=1
      junit --scan-class-path --include-tag addon-daten || status=1
    else
      junit --scan-class-path --exclude-tag addon-daten || status=1
    fi ;;
  *)
    echo "Aufruf: tools/fastcheck.sh [common|fabric|neoforge|all|test [junit-optionen]]" >&2; exit 2 ;;
esac
[ $status -eq 0 ] && echo "FASTCHECK $TARGET OK" || echo "FASTCHECK $TARGET FEHLER"
exit $status
