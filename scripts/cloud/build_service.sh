#!/usr/bin/env bash
set -euo pipefail
service="${1:?service required}"
image="${2:?image tag required}"
revision="${3:?source revision required}"
case "$service" in
  frontend)
    docker build --target cloud --label "org.opencontainers.image.revision=$revision" \
      --build-arg "VITE_GOOGLE_CLOUD_PROJECT=project-c9bd3d0e-266f-47bf-852" \
      --build-arg "VITE_GRAFANA_URL=${FRONTEND_GRAFANA_URL:-}" \
      -f frontend/Dockerfile -t "$image" frontend
    ;;
  auth-service|customer-service|payment-service|gateway-service|mock-bank-service|notification-service|api-gateway)
    mvn -B -pl "$service" -am package -DskipTests
    bash scripts/setup-otel-agent.sh
    cp observability/agent/opentelemetry-javaagent.jar "$service/target/opentelemetry-javaagent.jar"
    docker build --target cloud --label "org.opencontainers.image.revision=$revision" \
      --build-arg "JAR_FILE=target/$service-0.1.0-SNAPSHOT.jar" \
      -f "$service/Dockerfile" -t "$image" "$service"
    ;;
  *) echo 'Unsupported service' >&2; exit 1 ;;
esac
