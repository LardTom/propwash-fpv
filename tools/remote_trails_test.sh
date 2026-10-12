#!/bin/bash
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
LOADER=${1:-fabric}
WATCH=${PROPWASH_TRAIL_SECONDS:-20}
EXPECT=${PROPWASH_TRAIL_EXPECT:-joined|remote trail drawn|remote ground flag clear|!remote trail none}
LAUNCH=${PROPWASH_MC_LAUNCH:-}
HOSTDIR=$LOADER/runs/selftest-client/lan-trails
RESULT=$LOADER/runs/smoke-client/jointest/remote-trails.txt
OUT=${PROPWASH_TRAIL_LOGS:-$(mktemp -d /tmp/pw-remote-trails.XXXXXX)}
mkdir -p "$OUT"
rm -rf "$HOSTDIR" "$RESULT"

echo "Gastgeber ($LOADER, Selbsttest-Client) startet, Protokolle in $OUT"
PROPWASH_LANHOST=1 $LAUNCH ./gradlew -q ":$LOADER:runClientSelftest" -PpropwashSelftestOnly=lan-trails -PpropwashDevName=PwLanPilot \
  > "$OUT/host.log" 2>&1 &
HOST_PID=$!

PORT=
while kill -0 "$HOST_PID" 2>/dev/null; do
  if [ -s "$HOSTDIR/ready" ]; then
    PORT=$(cat "$HOSTDIR/ready")
    break
  fi
  sleep 5
done
if [ -z "$PORT" ]; then
  wait "$HOST_PID"
  echo "Gastgeber endete ohne LAN-Freigabe (Exit $?), siehe $OUT/host.log" >&2
  exit 1
fi

echo "LAN-Welt auf Port $PORT, Beobachter ($LOADER, Rauchtest-Client) tritt bei"
PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=remote-trails PROPWASH_JOINTEST_TRAIL="$WATCH" \
  PROPWASH_JOINTEST_EXPECT="$EXPECT" $LAUNCH ./gradlew -q ":$LOADER:runClientSmoke" -PpropwashDevName=PwLanWatcher \
  > "$OUT/observer.log" 2>&1
OBSERVER=$?
wait "$HOST_PID"
HOST=$?

echo "--- Beobachter (Exit $OBSERVER): $RESULT"
grep -E '^(remote|joined)' "$RESULT" 2>/dev/null
echo "--- Gastgeber (Exit $HOST)"
grep -aE 'LAN-Trails|lan-trails|SELFTEST (PASS|FAIL)|PROPWASH SELFTEST' "$OUT/host.log" | tail -n 8
[ "$OBSERVER" -eq 0 ] && [ "$HOST" -eq 0 ]
