#!/bin/bash
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
LOADER=${1:-fabric}
EXPECT=${PROPWASH_COV_CAMANGLE_EXPECT:-joined|camangle_spectate=follows|!camangle_spectate=stale}
LAUNCH=${PROPWASH_MC_LAUNCH:-}
HOSTDIR=$LOADER/runs/selftest-client/lan-cov-camangle
RESULT=$LOADER/runs/smoke-client/jointest/cov-camangle.txt
OUT=${PROPWASH_COV_CAMANGLE_LOGS:-$(mktemp -d /tmp/pw-remote-camangle.XXXXXX)}
mkdir -p "$OUT"
rm -rf "$HOSTDIR" "$RESULT"

echo "Gastgeber ($LOADER, Selbsttest-Client) startet, Protokolle in $OUT"
PROPWASH_LANHOST_COV_CAMANGLE=1 $LAUNCH ./gradlew -q ":$LOADER:runClientSelftest" -PpropwashSelftestOnly=lan-camangle -PpropwashDevName=PwLanPilot \
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

echo "LAN-Welt auf Port $PORT, Beobachter ($LOADER, Rauchtest-Client) tritt bei und schaut per F7 zu"
PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=cov-camangle PROPWASH_JOINTEST_COV_CAMANGLE=1 \
  PROPWASH_JOINTEST_EXPECT="$EXPECT" $LAUNCH ./gradlew -q ":$LOADER:runClientSmoke" -PpropwashDevName=PwLanWatcher \
  > "$OUT/observer.log" 2>&1
OBSERVER=$?
wait "$HOST_PID"
HOST=$?

echo "--- Beobachter (Exit $OBSERVER): $RESULT"
grep -E '^(camangle_|joined)' "$RESULT" 2>/dev/null
echo "--- Gastgeber (Exit $HOST)"
grep -aE 'LAN-Kamerawinkel|lan-camangle|PROPWASH SELFTEST' "$OUT/host.log" | tail -n 8
[ "$OBSERVER" -eq 0 ] && [ "$HOST" -eq 0 ]
