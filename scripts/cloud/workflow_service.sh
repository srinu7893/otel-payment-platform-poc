#!/usr/bin/env bash
# Shared manual workflow steps. WIF authentication remains owned by the workflow.
set -euo pipefail
: "${SERVICE:?}" "${REGISTRY:?}" "${COMMIT_SHA:?}"
case "$SERVICE" in
  frontend) image_name=payment-frontend ;;
  auth-service|customer-service|mock-bank-service|gateway-service|payment-service|notification-service|api-gateway) image_name="$SERVICE" ;;
  *) echo 'Unsupported selected service' >&2; exit 1 ;;
esac
case "${1:?step required}" in
  ci)
    run_id=$(gh api "repos/$GH_REPO/actions/workflows/ci.yml/runs" --method GET -f head_sha="$COMMIT_SHA" -f status=success --jq '.workflow_runs[0].id // empty')
    test -n "$run_id" || { echo 'Run existing CI successfully on this exact main commit first.'; exit 1; }
    ;;
  validate)
    test -n "$CLOUD_CONFIG" || { echo 'Set gcp-poc CLOUD_RUN_CONFIG_JSON.'; exit 1; }
    printf '%s' "$CLOUD_CONFIG" > "$RUNNER_TEMP/service-config.json"
    python3 scripts/cloud/deploy_service.py validate --service "$SERVICE" --config "$RUNNER_TEMP/service-config.json" \
      --image "$REGISTRY/$image_name@sha256:$(printf '0%.0s' {1..64})" --collector "${COLLECTOR_IMAGE:-}"
    ;;
  publish)
    gcloud auth configure-docker us-central1-docker.pkg.dev --quiet
    image="$REGISTRY/$image_name:$COMMIT_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
    bash scripts/cloud/build_service.sh "$SERVICE" "$image" "$COMMIT_SHA"
    docker push "$image"
    digest=$(gcloud artifacts docker images describe "$image" --project=project-c9bd3d0e-266f-47bf-852 --format='value(image_summary.digest)')
    [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid registry digest'; exit 1; }
    printf '%s' "$REGISTRY/$image_name@$digest" > "$RUNNER_TEMP/service-image.txt"
    ;;
  deploy)
    python3 scripts/cloud/deploy_service.py deploy --service "$SERVICE" --config "$RUNNER_TEMP/service-config.json" \
      --image "$(cat "$RUNNER_TEMP/service-image.txt")" --collector "${COLLECTOR_IMAGE:-}" \
      --revision "gh-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
    ;;
  *) echo 'Unsupported workflow step' >&2; exit 1 ;;
esac
