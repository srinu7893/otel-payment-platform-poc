#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8088}"

auth() {
  local username="$1"
  local password="$2"
  local body_file="/tmp/otel-login-${username}.json"
  local status
  for attempt in {1..30}; do
    status=$(curl --silent --show-error --output "$body_file" --write-out '%{http_code}' \
      -X POST "${BASE_URL}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -H "X-Correlation-Id: otel-login-${username}" \
      -d "{\"username\":\"${username}\",\"password\":\"${password}\"}")
    if [ "$status" = "200" ]; then
      jq -r '.accessToken' "$body_file"
      return 0
    fi
    sleep 1
  done
  echo "OTel acceptance login failed for ${username}" >&2
  cat "$body_file" >&2 || true
  return 1
}

echo '1/5 Verify observability components through backend'
SUPPORT_TOKEN=$(auth support support123)
OBS_READY=false
for attempt in {1..40}; do
  if curl --fail --silent --show-error \
    -H "Authorization: Bearer ${SUPPORT_TOKEN}" \
    -H 'X-Correlation-Id: otel-ops-health' \
    "${BASE_URL}/api/v1/ops/observability" >/tmp/observability.json; then
    cat /tmp/observability.json | jq .
    if [ "$(jq -r '.allHealthy' /tmp/observability.json)" = "true" ]; then
      OBS_READY=true
      break
    fi
  fi
  sleep 2
done
if [ "$OBS_READY" != "true" ]; then
  echo 'Observability backend did not report all components healthy'
  docker compose ps
  docker compose logs --no-color otel-collector jaeger prometheus grafana api-gateway
  exit 1
fi

echo '2/5 Verify Jaeger receives application services'
JAEGER_READY=false
for attempt in {1..40}; do
  if curl --fail --silent --show-error http://localhost:16686/api/services >/tmp/jaeger-services.json; then
    cat /tmp/jaeger-services.json | jq .
    if jq -e '.data | index("api-gateway") and index("payment-service") and index("gateway-service") and index("mock-bank-service")' /tmp/jaeger-services.json >/dev/null; then
      JAEGER_READY=true
      break
    fi
  fi
  sleep 2
done
if [ "$JAEGER_READY" != "true" ]; then
  echo 'Expected distributed-trace services not visible in Jaeger'
  docker compose logs --no-color otel-collector jaeger api-gateway payment-service gateway-service mock-bank-service
  exit 1
fi

echo '3/5 Verify Prometheus receives payment-service metrics'
METRICS_READY=false
for attempt in {1..40}; do
  curl --fail --silent --show-error -G http://localhost:9090/api/v1/query \
    --data-urlencode 'query=count({service_name="payment-service"})' >/tmp/prometheus-query.json || true
  if jq -e '.status == "success" and (.data.result | length) > 0' /tmp/prometheus-query.json >/dev/null 2>&1; then
    cat /tmp/prometheus-query.json | jq .
    METRICS_READY=true
    break
  fi
  sleep 2
done
if [ "$METRICS_READY" != "true" ]; then
  echo 'Payment-service metrics not visible in Prometheus'
  curl --silent http://localhost:9090/api/v1/label/__name__/values | jq . || true
  docker compose logs --no-color otel-collector prometheus payment-service
  exit 1
fi

echo '4/5 Verify Grafana is healthy and provisioned'
curl --fail --silent --show-error http://localhost:3001/api/health | tee /tmp/grafana-health.json | jq .
test "$(jq -r '.database' /tmp/grafana-health.json)" = "ok"

echo '5/5 Verify trace IDs are injected into structured application logs'
TRACE_LOG_FOUND=false
for attempt in {1..20}; do
  if docker compose logs --no-color api-gateway payment-service gateway-service mock-bank-service 2>/dev/null | grep -Eq '"trace_id":"[0-9a-f]{32}"'; then
    TRACE_LOG_FOUND=true
    break
  fi
  sleep 1
done
if [ "$TRACE_LOG_FOUND" != "true" ]; then
  echo 'No trace_id found in structured logs'
  docker compose logs --no-color api-gateway payment-service gateway-service mock-bank-service | tail -n 200
  exit 1
fi

echo 'OTEL ACCEPTANCE: PASS'
