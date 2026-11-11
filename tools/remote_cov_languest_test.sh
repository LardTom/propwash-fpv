#!/bin/bash
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
LOADER=${1:-fabric}
EXPECT=${PROPWASH_LANGUEST_EXPECT:-joined|cov_languest operator=no|cov_languest world_rules=readonly|cov_languest clear_in_tree=no|cov_languest clear_refused=yes}
LAUNCH=${PROPWASH_MC_LAUNCH:-}
HOSTDIR=$LOADER/runs/selftest-client/lan-cov-guest
RESULT=$LOADER/runs/smoke-client/jointest/cov-languest.txt
OUT=${PROPWASH_LANGUEST_LOGS:-$(mktemp -d /tmp/pw-cov-languest.XXXXXX)}
mkdir -p "$OUT"
rm -rf "$HOSTDIR" "$RESULT"

echo "Gastgeber ($LOADER, Selbsttest-Client) startet, Protokolle in $OUT"
PROPWASH_LANHOST_COV_GUEST=1 $LAUNCH ./gradlew -q ":$LOADER:runClientSelftest" '-PpropwashSelftestOnly=server: Abdeckung LAN-Gast' \
  -PpropwashDevName=PwLanHost > "$OUT/host.log" 2>&1 &
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

echo "LAN-Welt auf Port $PORT ohne Cheats für Gäste, Gast ($LOADER, Rauchtest-Client) tritt bei"
PROPWASH_JOINTEST="127.0.0.1:$PORT" PROPWASH_JOINTEST_LABEL=cov-languest PROPWASH_JOINTEST_COV_LANGUEST=1 \
  PROPWASH_JOINTEST_EXPECT="$EXPECT" $LAUNCH ./gradlew -q ":$LOADER:runClientSmoke" -PpropwashDevName=PwLanGuest \
  > "$OUT/guest.log" 2>&1
GUEST=$?
wait "$HOST_PID"
HOST=$?

echo "--- Gast (Exit $GUEST): $RESULT"
grep -E '^(cov_languest|joined)' "$RESULT" 2>/dev/null
echo "--- Gastgeber (Exit $HOST)"
grep -aE 'LAN-Gast|SELFTEST (PASS|FAIL)|PROPWASH SELFTEST' "$OUT/host.log" | tail -n 8
[ "$GUEST" -eq 0 ] && [ "$HOST" -eq 0 ]
