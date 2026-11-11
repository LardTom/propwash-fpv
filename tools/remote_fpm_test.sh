#!/bin/bash
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
LOADER=${1:-fabric}
WATCH=${PROPWASH_FPM_SECONDS:-20}
EXPECT=${PROPWASH_FPM_EXPECT:-joined|spectate_fpm=drawn|!spectate_fpm=none}
LAUNCH=${PROPWASH_MC_LAUNCH:-}
HOSTDIR=$LOADER/runs/selftest-client/lan-fpm
RESULT=$LOADER/runs/smoke-client/jointest/spectate-fpm.txt
PHOTO=$LOADER/runs/smoke-client/jointest/spectate-fpm.png
OUT=${PROPWASH_FPM_LOGS:-$(mktemp -d /tmp/pw-remote-fpm.XXXXXX)}
mkdir -p "$OUT"
rm -rf "$HOSTDIR" "$RESULT" "$PHOTO"

echo "Gastgeber ($LOADER, Selbsttest-Client) startet, Protokolle in $OUT"
PROPWASH_LANHOST_FPM=1 $LAUNCH ./gradlew -q ":$LOADER:runClientSelftest" -PpropwashSelftestOnly=lan-fpm -PpropwashDevName=PwLanPilot \
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
PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=spectate-fpm PROPWASH_JOINTEST_SPECTATE_FPM="$WATCH" \
  PROPWASH_JOINTEST_EXPECT="$EXPECT" $LAUNCH ./gradlew -q ":$LOADER:runClientSmoke" -PpropwashDevName=PwLanWatcher \
  > "$OUT/observer.log" 2>&1
OBSERVER=$?
wait "$HOST_PID"
HOST=$?

echo "--- Beobachter (Exit $OBSERVER): $RESULT"
grep -E '^(spectate_fpm|joined)' "$RESULT" 2>/dev/null
[ -s "$PHOTO" ] && echo "Bildschirmfoto: $PHOTO"
echo "--- Gastgeber (Exit $HOST)"
grep -aE 'LAN-Marker|lan-fpm|SELFTEST (PASS|FAIL)|PROPWASH SELFTEST' "$OUT/host.log" | tail -n 8
[ "$OBSERVER" -eq 0 ] && [ "$HOST" -eq 0 ]
