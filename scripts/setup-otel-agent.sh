#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Pin upgrades explicitly; digest from the official GitHub release asset metadata.
version=2.20.0
base="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${version}"
mkdir -p observability/agent
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl --fail --location --retry 3 "$base/opentelemetry-javaagent.jar" -o "$tmp/agent.jar"
expected=191666bf4346ab0677f92304bb272cd85f5954f49c03bb9636134e59c9a3276d
[[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]]
printf '%s  %s\n' "$expected" "$tmp/agent.jar" | sha256sum --check --status
mv "$tmp/agent.jar" observability/agent/opentelemetry-javaagent.jar
printf 'Verified OpenTelemetry Java agent %s\n' "$version"
