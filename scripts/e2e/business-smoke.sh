#!/usr/bin/env bash
# Run on a fresh, disposable demo stack; credentials stay in a private temp directory.
umask 077
run_dir=$(mktemp -d)
trap 'rm -rf "$run_dir"' EXIT
set -euo pipefail

api_call() {
  local name="$1"
  local output="$2"
  shift 2
  local status
  status=$(curl --silent --show-error --output "$output" --write-out '%{http_code}' "$@")
  echo "${name} HTTP ${status}"
  if [[ "$name" != "login" ]]; then cat "$output"; fi
  echo
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "${name} failed; dumping relevant service logs"
    bash scripts/otel-compose.sh logs --no-color api-gateway auth-service payment-service gateway-service mock-bank-service notification-service
    exit 1
  fi
}

api_expect_status() {
  local expected="$1"
  local name="$2"
  local output="$3"
  shift 3
  local status
  status=$(curl --silent --show-error --output "$output" --write-out '%{http_code}' "$@")
  echo "${name} HTTP ${status} (expected ${expected})"
  if [[ "$name" != "login" ]]; then cat "$output"; fi
  echo
  if [ "$status" != "$expected" ]; then
    echo "${name} returned unexpected status"
    bash scripts/otel-compose.sh logs --no-color api-gateway auth-service payment-service gateway-service mock-bank-service notification-service
    exit 1
  fi
}

api_call "login" ${run_dir}/login.json -X POST http://localhost:8088/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-login-001' \
  -d '{"username":"demo","password":"demo123"}'
TOKEN=$(jq -r '.accessToken' ${run_dir}/login.json)
test -n "$TOKEN"
test "$TOKEN" != "null"

api_call "payment" ${run_dir}/payment.json -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-payment-001' \
  -d '{"idempotencyKey":"ci-pay-001","accountNumber":"ACC1001","merchant":"CI Demo Store","amount":25.00}'
test "$(jq -r '.status' ${run_dir}/payment.json)" = "COMPLETED"
PAYMENT_ID=$(jq -r '.paymentId' ${run_dir}/payment.json)

NOTIFICATION_FOUND=false
for attempt in {1..30}; do
  api_call "payment notification poll" ${run_dir}/notifications.json \
    -X GET "http://localhost:8088/api/v1/notifications?paymentId=${PAYMENT_ID}&page=0&size=20" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'X-Correlation-Id: ci-notification-poll-001'
  if jq -e --arg paymentId "$PAYMENT_ID" '.content[]? | select(.paymentId == $paymentId and .eventType == "PAYMENT_COMPLETED" and .status == "SENT")' ${run_dir}/notifications.json >/dev/null; then
    NOTIFICATION_FOUND=true
    break
  fi
  sleep 1
done
if [ "$NOTIFICATION_FOUND" != "true" ]; then
  echo "No SENT PAYMENT_COMPLETED notification observed for payment ${PAYMENT_ID}"
  bash scripts/otel-compose.sh logs --no-color payment-service notification-service rabbitmq
  exit 1
fi

api_call "payment idempotent replay" ${run_dir}/payment-replay.json -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-payment-replay-001' \
  -d '{"idempotencyKey":"ci-pay-001","accountNumber":"ACC1001","merchant":"CI Demo Store","amount":25.00}'
test "$(jq -r '.status' ${run_dir}/payment-replay.json)" = "COMPLETED"
test "$(jq -r '.paymentId' ${run_dir}/payment-replay.json)" = "$PAYMENT_ID"

api_call "refund" ${run_dir}/refund.json -X POST "http://localhost:8088/api/v1/payments/${PAYMENT_ID}/refunds" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-refund-001' \
  -d '{"idempotencyKey":"ci-refund-001"}'
test "$(jq -r '.status' ${run_dir}/refund.json)" = "COMPLETED"
test "$(jq -r '.paymentId' ${run_dir}/refund.json)" = "$PAYMENT_ID"
REFUND_ID=$(jq -r '.refundId' ${run_dir}/refund.json)
test -n "$REFUND_ID"
test "$REFUND_ID" != "null"

api_call "refund idempotent replay" ${run_dir}/refund-replay.json -X POST "http://localhost:8088/api/v1/payments/${PAYMENT_ID}/refunds" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-refund-replay-001' \
  -d '{"idempotencyKey":"ci-refund-001"}'
test "$(jq -r '.status' ${run_dir}/refund-replay.json)" = "COMPLETED"
test "$(jq -r '.refundId' ${run_dir}/refund-replay.json)" = "$REFUND_ID"

REFUND_NOTIFICATION_FOUND=false
for attempt in {1..30}; do
  api_call "refund notification poll" ${run_dir}/refund-notifications.json \
    -X GET "http://localhost:8088/api/v1/notifications?page=0&size=20" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'X-Correlation-Id: ci-refund-notification-poll-001'
  if jq -e --arg paymentId "$PAYMENT_ID" '.content[]? | select(.paymentId == $paymentId and .eventType == "PAYMENT_REFUNDED" and .status == "SENT")' ${run_dir}/refund-notifications.json >/dev/null; then
    REFUND_NOTIFICATION_FOUND=true
    break
  fi
  sleep 1
done
if [ "$REFUND_NOTIFICATION_FOUND" != "true" ]; then
  echo "No SENT PAYMENT_REFUNDED notification observed for payment ${PAYMENT_ID}"
  bash scripts/otel-compose.sh logs --no-color payment-service notification-service rabbitmq gateway-service mock-bank-service
  exit 1
fi

api_call "get refund" ${run_dir}/refund-get.json -X GET "http://localhost:8088/api/v1/payments/${PAYMENT_ID}/refund" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Correlation-Id: ci-refund-get-001'
test "$(jq -r '.refundId' ${run_dir}/refund-get.json)" = "$REFUND_ID"
test "$(jq -r '.status' ${run_dir}/refund-get.json)" = "COMPLETED"

api_call "insufficient funds payment" ${run_dir}/payment-insufficient.json -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-payment-insufficient-001' \
  -d '{"idempotencyKey":"ci-pay-insufficient-001","accountNumber":"ACC1001","merchant":"CI Expensive Store","amount":6000.00}'
test "$(jq -r '.status' ${run_dir}/payment-insufficient.json)" = "FAILED"

api_expect_status "422" "risk limit payment" ${run_dir}/payment-risk.json -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-payment-risk-001' \
  -d '{"idempotencyKey":"ci-pay-risk-001","accountNumber":"ACC1001","merchant":"CI Risk Store","amount":100001.00}'
test "$(jq -r '.code' ${run_dir}/payment-risk.json)" = "RISK_LIMIT_EXCEEDED"
test "$(jq -r '.fieldErrors.rule' ${run_dir}/payment-risk.json)" = "MAX_TRANSACTION_AMOUNT"

api_call "transfer" ${run_dir}/transfer.json -X POST http://localhost:8088/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: ci-transfer-001' \
  -d '{"idempotencyKey":"ci-transfer-001","senderAccount":"ACC1001","receiverAccount":"ACC2001","amount":10.00,"currency":"INR"}'
test "$(jq -r '.status' ${run_dir}/transfer.json)" = "COMPLETED"
