# Build once, publish, deploy later

Workflow: .github/workflows/manual-cloud-images.yml.
Changes are on PR #3's feature branch; merge/review and passing exact-main CI/E2E are prerequisites.
This new workflow has not yet been executed against GCP.

## Publish

Run manual-cloud-images on main with action=publish.
It builds/tests seven Java services, frontend and Collector, checks Collector configuration,
authenticates using WIF and pushes nine images to Artifact Registry.
No Cloud Run service or traffic mutation occurs in publish mode.
A successful run uploads published-images-RUN_ID with:
- images.json: nine immutable registry digest references
- commit.txt: exact source commit
- build-settings.json: registry coordinates and frontend Grafana URL, checked before deployment

The following manifests are generated during deploy, once real deployment configuration exists:
- manifests/cloudrun-frontend.yaml
- manifests/cloudrun-api-gateway.yaml
- manifests/cloudrun-auth-service.yaml
- manifests/cloudrun-customer-service.yaml
- manifests/cloudrun-payment-service.yaml
- manifests/cloudrun-gateway-service.yaml
- manifests/cloudrun-mock-bank-service.yaml
- manifests/cloudrun-notification-service.yaml

The YAML files are generated from the existing shared manifest renderer rather than independently maintained copies.
Each Java YAML contains the application plus Collector sidecar, appropriate environment/secret references,
runtime service account, probes and network settings. The frontend has one container and no app secrets.
The Collector is a sidecar image, not a ninth independent Cloud Run service.

## Deploy existing images

Run the SAME workflow at the SAME main commit with action=deploy and publish_run_id=successful publish run ID.
Build and Docker steps are skipped. The run identity, workflow path, main branch, event, success,
source commit, build-settings equality and artifact source are checked. Deployment configuration is validated at deploy time.
The run uses published immutable digests; no image rebuild or repush is performed.
Fresh YAML artifacts are rendered. The existing guarded release uses equivalent JSON service manifests
to preserve current traffic, stage candidate revisions, verify readiness, promote exact revisions
and restore previous traffic on detected failure. YAML is for review/manual inspection; it does not
replace those safeguards.

Cloud Run imports the referenced image during deployment; users do not manually pull it first.
Pushing a new image alone does not update running revisions.

## Setup still required

Create Artifact Registry, WIF trust for this repo, deployment/runtime accounts, Secret Manager values,
network/subnet, durable PostgreSQL and RabbitMQ/TLS, initial eight Cloud Run services, canonical URLs,
and private/public caller IAM as described in CLOUD_RUN_PLAN.md.
Publishing needs only registry coordinates; CLOUD_RUN_CONFIG_JSON is required only for deploy.
This workflow does not bootstrap infrastructure or eliminate the deployment canonical URL requirement.

GitHub environment gcp-poc:
- secrets WIF_PROVIDER, GCP_SERVICE_ACCOUNT (registry publisher)
- secret GCP_DEPLOY_SERVICE_ACCOUNT (separate deployment identity, required only for deploy)
- optional variables GCP_PROJECT_ID, GCP_REGION, ARTIFACT_REGISTRY_REPOSITORY; defaults are project-c9bd3d0e-266f-47bf-852, us-central1, otel-payment-platform
- variable CLOUD_RUN_CONFIG_JSON from deploy/cloud-run/config.example.json with real values
- optional variable GRAFANA_URL

## Manual YAML use

After downloading the generated artifact and reviewing settings:

    gcloud run services replace manifests/cloudrun-frontend.yaml --project=YOUR_PROJECT --region=YOUR_REGION

This direct command is intentionally not the recommended guarded update path:
IAM bindings are separate, and traffic/staging must be reviewed before use.
First creation may require two-pass bootstrapping to establish canonical URLs before configuring callers.
Use the guarded workflow for normal updates. Run manual-cloud-acceptance separately after release.

## Cancellation findings

The API returned six cancelled payment-platform-ci runs dated 2026-10-04.
No October 5 cancelled Cloud Run deployment was established from those results.
A publish-only run cleanly separates image preparation from deployment failure.

## Validation boundary

Source reviewed against existing build/publish/render/release contracts.
Local validation: seven release unit tests pass; workflow YAML parses; mocked publication verifies minimal configuration, nine pushes, immutable digests and output directory creation. These checks are not evidence of a live registry push.
Run publish only after review, exact-commit CI/manual E2E and GCP setup; do not infer live cloud success.
