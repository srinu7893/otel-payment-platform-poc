#!/usr/bin/env bash
set -euo pipefail
umask 077
run_dir=$(mktemp -d)
trap 'rm -rf "$run_dir"' EXIT

BASE_URL="${BASE_URL:-http://localhost:8088}"

auth() {
  local username="$1"
  local password="$2"
  local body_file="${run_dir}/login-${username}.json"
  local status
  for attempt in {1..20}; do
    status=$(curl --silent --show-error --output "$body_file" --write-out '%{http_code}' \
      -X POST "${BASE_URL}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -H "X-Correlation-Id: pre-otel-login-${username}" \
      -d "{\"username\":\"${username}\",\"password\":\"${password}\"}")
    if [ "$status" = "200" ]; then
      jq -r '.accessToken' "$body_file"
      return 0
    fi
    sleep 1
  done
  echo "Login failed for ${username} after startup retry window (last HTTP ${status})" >&2
  cat "$body_file" >&2 || true
  return 1
}

post_payment() {
  local token="$1"
  local account="$2"
  local key="$3"
  local correlation="$4"
  curl --fail --silent --show-error \
    -X POST "${BASE_URL}/api/v1/payments" \
    -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' \
    -H "X-Correlation-Id: ${correlation}" \
    -d "{\"idempotencyKey\":\"${key}\",\"accountNumber\":\"${account}\",\"merchant\":\"Pre-OTel Failure Lab\",\"amount\":10.00}"
}

echo '1/4 Verify public OpenAPI/Swagger contract'
# Read the complete response before checking it: an early-exiting pipe reader
# can make curl exit 23 under pipefail even when the contract is valid.
curl --fail --silent --show-error --output "${run_dir}/openapi.yaml" "${BASE_URL}/openapi.yaml"
python3 -c 'import pathlib,sys; assert pathlib.Path(sys.argv[1]).read_text().startswith("openapi: 3.0.3")' "${run_dir}/openapi.yaml"
curl --fail --silent --show-error -L "${BASE_URL}/swagger-ui.html" >/dev/null

echo '2/4 Verify SUPPORT operations health'
SUPPORT_TOKEN=$(auth support support123)
test -n "$SUPPORT_TOKEN"
HEALTH=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${SUPPORT_TOKEN}" \
  -H 'X-Correlation-Id: pre-otel-ops-health' \
  "${BASE_URL}/api/v1/ops/health")
echo "$HEALTH" | jq .
test "$(echo "$HEALTH" | jq -r '.allHealthy')" = "true"
test "$(echo "$HEALTH" | jq '[.services[] | select(.status != "UP")] | length')" = "0"

echo '3/4 Verify deterministic slow-bank timeout maps to reconciliation'
SLOW_TOKEN=$(auth slowdemo slowdemo123)
SLOW_KEY="pre-otel-slow-$(date +%s%N)"
SLOW_RESPONSE=$(post_payment "$SLOW_TOKEN" ACC-SLOW "$SLOW_KEY" pre-otel-slow-payment)
echo "$SLOW_RESPONSE" | jq .
test "$(echo "$SLOW_RESPONSE" | jq -r '.status')" = "RECONCILIATION_REQUIRED"

echo '4/4 Verify simulated bank 500 maps to reconciliation'
ERROR_TOKEN=$(auth errordemo errordemo123)
ERROR_KEY="pre-otel-error-$(date +%s%N)"
ERROR_RESPONSE=$(post_payment "$ERROR_TOKEN" ACC-ERROR "$ERROR_KEY" pre-otel-error-payment)
echo "$ERROR_RESPONSE" | jq .
test "$(echo "$ERROR_RESPONSE" | jq -r '.status')" = "RECONCILIATION_REQUIRED"

echo 'PRE-OTEL ACCEPTANCE: PASS'
