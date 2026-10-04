#!/usr/bin/env bash
set -euo pipefail
ports=(8079 8084 8082 8081 8080 8083 8088)
for port in "${ports[@]}"; do
  echo "Waiting for service on port ${port}..."
  for attempt in {1..60}; do
    if curl --fail --silent "http://localhost:${port}/actuator/health" >/dev/null; then
      echo "Port ${port} healthy"
      break
    fi
    if [ "$attempt" -eq 60 ]; then
      echo "Service on port ${port} did not become healthy"
      bash scripts/otel-compose.sh ps
      bash scripts/otel-compose.sh logs --no-color
      exit 1
    fi
    sleep 2
  done
done
