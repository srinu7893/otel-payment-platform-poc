#!/usr/bin/env bash
# Local operator convenience; acceptance requires a fresh disposable demo stack.
set -euo pipefail
cd "$(dirname "$0")/.."
action=${1:-start}
export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-otel-demo}
case "$action" in
  start)
    for dependency in mvn docker curl sha256sum; do
      command -v "$dependency" >/dev/null || { echo "Required command missing: $dependency" >&2; exit 1; }
    done
    mvn -B package -DskipTests
    bash scripts/setup-otel-agent.sh
    bash scripts/otel-compose.sh config --quiet
    bash scripts/otel-compose.sh up --build -d
    bash scripts/e2e/wait-ready.sh
    echo 'Customer UI: http://localhost:3000 | Grafana: http://localhost:3001'
    ;;
  test)
    echo 'Run on fresh disposable state; fixed baseline IDs are intentionally deterministic.'
    bash scripts/e2e/business-smoke.sh
    RESILIENCE=${RESILIENCE:-true} python3 scripts/e2e/otel_acceptance.py
    bash scripts/pre_otel_acceptance.sh
    python3 scripts/e2e/live-traffic.py
    python3 scripts/e2e/capture-evidence.py
    python3 scripts/render-poc-guide.py
    ;;
  report)
    python3 scripts/e2e/capture-evidence.py
    python3 scripts/render-poc-guide.py
    ;;
  stop)
    bash scripts/otel-compose.sh down
    ;;
  reset)
    echo "Deleting disposable demo state and telemetry volumes for project: $COMPOSE_PROJECT_NAME"
    bash scripts/otel-compose.sh down -v --remove-orphans
    ;;
  *)
    echo 'Usage: bash scripts/run-poc.sh {start|test|report|stop|reset}' >&2
    exit 2
    ;;
esac
