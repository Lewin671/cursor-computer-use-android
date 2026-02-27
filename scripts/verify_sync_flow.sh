#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
PORT="${PORT:-18080}"
BASE_URL="http://127.0.0.1:${PORT}/api/v1"
DB_FILE="${ROOT_DIR}/tmp/verify_sync.db"
LOG_FILE="${ROOT_DIR}/tmp/verify_sync_backend.log"

mkdir -p "${ROOT_DIR}/tmp"
rm -f "${DB_FILE}" "${LOG_FILE}"

APP_ADDR=":${PORT}" DB_PATH="${DB_FILE}" JWT_SECRET="verify-secret" \
  bash -c "cd \"${BACKEND_DIR}\" && go run ./cmd/server" >"${LOG_FILE}" 2>&1 &
SERVER_PID=$!
trap 'kill ${SERVER_PID} >/dev/null 2>&1 || true' EXIT

wait_for_server() {
  for _ in $(seq 1 30); do
    if curl -s "http://127.0.0.1:${PORT}/health" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "backend not ready"
  cat "${LOG_FILE}" || true
  exit 1
}

json_field() {
  local key="$1"
  python3 -c '
import json,sys
key = sys.argv[1]
obj = json.loads(sys.stdin.read())
value = obj
for part in key.split("."):
    if isinstance(value, list) and part.isdigit():
        value = value[int(part)]
    elif isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break
print("" if value is None else value)
' "$key"
}

assert_eq() {
  local actual="$1"
  local expected="$2"
  local msg="$3"
  if [ "${actual}" != "${expected}" ]; then
    echo "ASSERT FAILED: ${msg}. expected=${expected} actual=${actual}"
    cat "${LOG_FILE}" || true
    exit 1
  fi
}

assert_non_empty() {
  local actual="$1"
  local msg="$2"
  if [ -z "${actual}" ]; then
    echo "ASSERT FAILED: ${msg}"
    cat "${LOG_FILE}" || true
    exit 1
  fi
}

wait_for_server
echo "[1/10] register user"
REGISTER_PAYLOAD='{"email":"demo@example.com","password":"123456"}'
REGISTER_RES="$(curl -s -X POST "${BASE_URL}/auth/register" -H 'Content-Type: application/json' -d "${REGISTER_PAYLOAD}")"
USER_ID="$(printf '%s' "${REGISTER_RES}" | json_field "userId")"
ACCESS_A="$(printf '%s' "${REGISTER_RES}" | json_field "accessToken")"
REFRESH_A="$(printf '%s' "${REGISTER_RES}" | json_field "refreshToken")"
assert_non_empty "${USER_ID}" "register should return user id"
assert_non_empty "${ACCESS_A}" "register should return access token"

echo "[2/10] login as device B"
LOGIN_RES_B="$(curl -s -X POST "${BASE_URL}/auth/login" -H 'Content-Type: application/json' -d '{"email":"demo@example.com","password":"123456"}')"
ACCESS_B="$(printf '%s' "${LOGIN_RES_B}" | json_field "accessToken")"
REFRESH_B="$(printf '%s' "${LOGIN_RES_B}" | json_field "refreshToken")"
assert_non_empty "${ACCESS_B}" "login on device B should return access token"

echo "[3/10] initial state fetch"
STATE_A_1="$(curl -s "${BASE_URL}/timer/state" -H "Authorization: Bearer ${ACCESS_A}")"
VER_1="$(printf '%s' "${STATE_A_1}" | json_field "version")"
STATUS_1="$(printf '%s' "${STATE_A_1}" | json_field "status")"
assert_eq "${STATUS_1}" "idle" "initial timer status should be idle"

echo "[4/10] device A start timer"
START_RES="$(curl -s -X POST "${BASE_URL}/timer/start" -H "Authorization: Bearer ${ACCESS_A}" -H 'Content-Type: application/json' -d "{\"mode\":\"focus\",\"durationSec\":60,\"clientVersion\":${VER_1}}")"
STATUS_AFTER_START="$(printf '%s' "${START_RES}" | json_field "status")"
VER_AFTER_START="$(printf '%s' "${START_RES}" | json_field "version")"
assert_eq "${STATUS_AFTER_START}" "running" "timer should be running after start"

echo "[5/10] simulate app kill/restart by waiting and refetching state"
sleep 2
STATE_A_RESTART="$(curl -s "${BASE_URL}/timer/state" -H "Authorization: Bearer ${ACCESS_A}")"
STATUS_RESTART="$(printf '%s' "${STATE_A_RESTART}" | json_field "status")"
REMAIN_RESTART="$(printf '%s' "${STATE_A_RESTART}" | json_field "remainingSec")"
assert_eq "${STATUS_RESTART}" "running" "state after restart should still be running"
assert_non_empty "${REMAIN_RESTART}" "remaining time should exist after restart"

echo "[6/10] device B sync and verify same state"
STATE_B="$(curl -s "${BASE_URL}/timer/state" -H "Authorization: Bearer ${ACCESS_B}")"
STATUS_B="$(printf '%s' "${STATE_B}" | json_field "status")"
assert_eq "${STATUS_B}" "running" "device B should see running timer"

echo "[7/10] concurrent mutation conflict (A pause, B reset with stale version)"
PAUSE_A="$(curl -s -X POST "${BASE_URL}/timer/pause" -H "Authorization: Bearer ${ACCESS_A}" -H 'Content-Type: application/json' -d "{\"clientVersion\":${VER_AFTER_START}}")"
VER_AFTER_PAUSE="$(printf '%s' "${PAUSE_A}" | json_field "version")"
RESET_B_WITH_STALE="$(curl -s -o /tmp/reset_b_resp.json -w '%{http_code}' -X POST "${BASE_URL}/timer/reset" -H "Authorization: Bearer ${ACCESS_B}" -H 'Content-Type: application/json' -d "{\"clientVersion\":${VER_AFTER_START}}")"
assert_eq "${RESET_B_WITH_STALE}" "409" "stale device mutation must return 409"

echo "[8/10] complete session and verify history sync"
START_AGAIN="$(curl -s -X POST "${BASE_URL}/timer/start" -H "Authorization: Bearer ${ACCESS_B}" -H 'Content-Type: application/json' -d "{\"mode\":\"focus\",\"durationSec\":60,\"clientVersion\":${VER_AFTER_PAUSE}}")"
VER_RUNNING_2="$(printf '%s' "${START_AGAIN}" | json_field "version")"
curl -s -X POST "${BASE_URL}/timer/complete" -H "Authorization: Bearer ${ACCESS_B}" -H 'Content-Type: application/json' -d "{\"clientVersion\":${VER_RUNNING_2}}" >/tmp/complete_resp.json
HISTORY_A="$(curl -s "${BASE_URL}/timer/history?since=0" -H "Authorization: Bearer ${ACCESS_A}")"
HISTORY_B="$(curl -s "${BASE_URL}/timer/history?since=0" -H "Authorization: Bearer ${ACCESS_B}")"
COUNT_A="$(printf '%s' "${HISTORY_A}" | python3 - <<'PY'
COUNT_A="$(printf '%s' "${HISTORY_A}" | python3 -c 'import json,sys; print(len(json.loads(sys.stdin.read()).get("items", [])))')"
COUNT_B="$(printf '%s' "${HISTORY_B}" | python3 -c 'import json,sys; print(len(json.loads(sys.stdin.read()).get("items", [])))')"
assert_eq "${COUNT_A}" "${COUNT_B}" "history count should match on both devices"
if [ "${COUNT_A}" -le 0 ]; then
  echo "ASSERT FAILED: history count should be greater than zero"
  exit 1
fi

echo "[9/10] refresh token and re-auth state call"
REFRESH_RES="$(curl -s -X POST "${BASE_URL}/auth/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"${REFRESH_A}\"}")"
ACCESS_A_NEW="$(printf '%s' "${REFRESH_RES}" | json_field "accessToken")"
assert_non_empty "${ACCESS_A_NEW}" "refresh should provide new access token"
STATE_AFTER_REFRESH="$(curl -s "${BASE_URL}/timer/state" -H "Authorization: Bearer ${ACCESS_A_NEW}")"
assert_non_empty "${STATE_AFTER_REFRESH}" "state call with refreshed token should succeed"

echo "[10/10] CORS and user isolation checks"
CORS_HEADER="$(curl -s -D - -o /dev/null -X OPTIONS "${BASE_URL}/auth/login" -H 'Origin: http://localhost:3000' -H 'Access-Control-Request-Method: POST' | tr -d '\r' | awk 'BEGIN{IGNORECASE=1} /^Access-Control-Allow-Origin:/ {print $2}')"
assert_non_empty "${CORS_HEADER}" "CORS allow origin header should be present"
REGISTER_2="$(curl -s -X POST "${BASE_URL}/auth/register" -H 'Content-Type: application/json' -d '{"email":"other@example.com","password":"123456"}')"
ACCESS_2="$(printf '%s' "${REGISTER_2}" | json_field "accessToken")"
HISTORY_2="$(curl -s "${BASE_URL}/timer/history?since=0" -H "Authorization: Bearer ${ACCESS_2}")"
COUNT_2="$(printf '%s' "${HISTORY_2}" | python3 - <<'PY'
COUNT_2="$(printf '%s' "${HISTORY_2}" | python3 -c 'import json,sys; print(len(json.loads(sys.stdin.read()).get("items", [])))')"
assert_eq "${COUNT_2}" "0" "new user should not see other user's history"

echo "ALL_CHECKS_PASSED"
