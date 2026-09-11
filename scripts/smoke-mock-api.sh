#!/usr/bin/env bash
# Smoke the LAN mock API (register / config / playlist / heartbeat / events).
# Usage: ./scripts/smoke-mock-api.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${TAP_MOCK_PORT:-18787}"
HOST="${TAP_MOCK_HOST:-127.0.0.1}"
python3 "$ROOT/server/mock_api.py" --host "$HOST" --port "$PORT" >/tmp/tablead-mock-api.log 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; }
trap cleanup EXIT
ok=0
for _ in $(seq 1 50); do
  if curl -sf "http://$HOST:$PORT/health" >/dev/null; then
    ok=1
    break
  fi
  sleep 0.1
done
if [[ "$ok" != 1 ]]; then
  echo "mock API did not become healthy on $HOST:$PORT" >&2
  cat /tmp/tablead-mock-api.log >&2 || true
  exit 1
fi
curl -sf -X POST "http://$HOST:$PORT/v1/device/register" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: TABLE-abcd1234" \
  -d '{"deviceId":"TABLE-abcd1234","appVersion":"0.8.0","applicationId":"com.tableadplayer.app.debug","demoMode":true}' \
  | python3 -c "import json,sys; b=json.load(sys.stdin); assert b['status']=='REGISTERED', b"
curl -sf "http://$HOST:$PORT/v1/device/config?deviceId=TABLE-abcd1234" -H "X-Device-Id: TABLE-abcd1234" >/dev/null
curl -sf "http://$HOST:$PORT/v1/playlists/current" >/dev/null
curl -sf -X POST "http://$HOST:$PORT/v1/device/heartbeat" \
  -H "Content-Type: application/json" -H "X-Device-Id: TABLE-abcd1234" \
  -d '{"deviceId":"TABLE-abcd1234","appVersion":"0.8.0","capturedAt":"2026-01-01T00:00:00Z"}' >/dev/null
curl -sf -X POST "http://$HOST:$PORT/v1/device/events" \
  -H "Content-Type: application/json" -H "X-Device-Id: TABLE-abcd1234" \
  -d '{"deviceId":"TABLE-abcd1234","events":[{"type":"play","itemId":"slide-welcome","at":"2026-01-01T00:00:01Z"}]}' >/dev/null
echo "mock API smoke ok on http://$HOST:$PORT/"
