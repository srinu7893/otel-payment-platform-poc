# Manual Cloud Run deployment per service

Each of the eight `deploy-*.yml` workflows has only `workflow_dispatch`. A push runs the existing CI, never these deployments. In GitHub open **Actions → Deploy <service> → Run workflow → main**. Only the selected application's Dockerfile is built, its image is pushed, and its dedicated service receives a new revision. Backend Maven builds use `-pl <service> -am`: the parent/shared `cloud-runtime` dependency is packaged when needed; other application services are not built. Frontend builds do not run Maven.

All application images go to `us-central1-docker.pkg.dev/project-c9bd3d0e-266f-47bf-852/otel-payment-platform`. Every run uses a commit/run-specific tag and deploys its resolved immutable digest. Backend images use the `cloud` target of their service Dockerfile, including the checksum-verified Java agent. The `local` target remains the default for Compose. Frontend's `cloud` target listens on Cloud Run's injected `PORT` and proxies `/api` to the configured API Gateway HTTPS origin; the local target retains Compose networking.

## Configure GitHub and GCP once

Create the GitHub environment `gcp-poc`, with these secrets:

| Secret | Value |
|---|---|
| `WIF_PROVIDER` | Full workload identity provider resource name, bound to this repository/main/environment |
| `GCP_SERVICE_ACCOUNT` | Deployment service account email, authenticated through WIF; no JSON key |

Environment variables:

| Variable | Value |
|---|---|
| `CLOUD_RUN_CONFIG_JSON` | Partial JSON based on `deploy/cloud-run/config.example.json`; only the selected service URL/account and its required secrets are mandatory |
| `COLLECTOR_IMAGE` | Existing Collector image digest in the same registry, e.g. `us-central1-docker.pkg.dev/project-c9bd3d0e-266f-47bf-852/otel-payment-platform/collector@sha256:...` |
| `GRAFANA_URL` | Optional Grafana UI URL for frontend links |

Backend workflows reuse the configured Collector digest; they never build another service or the Collector. Publish that shared image once with the existing image-publishing tooling or a separate approved Collector release. Frontend does not require a Collector digest.

Enable Artifact Registry, Cloud Run, IAM credentials, Secret Manager and telemetry APIs, and provision the repository in `us-central1`. The deployment identity needs Artifact Registry writer, Cloud Run deployment permissions, `iam.serviceAccounts.actAs` on the runtime accounts, and token creation for the selected runtime health-check identities. Bind the WIF subject with `roles/iam.workloadIdentityUser`. Runtime accounts need access to their required secrets and telemetry writer roles.

Provision the database for each database consumer. Neon can be reached over public TLS without a VPC. Configure TLS RabbitMQ only for Payment and Notification; provide network/subnetwork together only when private networking is needed. JDBC URL/user/password and JWT signing key are supplied through Secret Manager, never workflow text. All JWT-using services must use the same signing-key secret/version. The five database consumers are Auth, Customer, Bank, Payment and Notification. API Gateway and Gateway adapter have no datasource dependency. Payment and Notification need broker secrets; Payment's background relay and Notification's consumers use instance-based CPU and a minimum instance of one.

The deployer intentionally does not guess IAM or create infrastructure. Bootstrap only the selected service slot to obtain its stable canonical URL. Add other slots, URLs and accounts as they become available. A shared runtime service account is supported, including the existing Auth and Customer runtime identity. Set runtime self-invoker permissions and caller IAM as each caller is introduced. Internal services must reject anonymous requests; only frontend and API Gateway are public edges. This bootstrap step does not deploy the application. The first successful application revision must pass startup/readiness before receiving traffic. The frontend proxy expects an API Gateway accessible to the configured public edge.

IAM caller graph: Payment → Customer/Gateway adapter; Gateway adapter → Bank; API Gateway → all six backend services for support health. Provision each binding before enabling that caller. Selected-service deployment checks only its self-invoker binding; missing future callers do not block it. The application already supports Cloud Run IAM tokens through `X-Serverless-Authorization`; customer JWTs stay in `Authorization`.

## Customer-only configuration

Set `gcp-poc` environment variable `CLOUD_RUN_CONFIG_JSON` to:

```json
{
  "project": "project-c9bd3d0e-266f-47bf-852",
  "region": "us-central1",
  "repository": "otel-payment-platform",
  "urls": {
    "customer-service": "https://customer-service-52916499838.us-central1.run.app"
  },
  "serviceAccounts": {
    "customer-service": "otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com"
  },
  "databaseSecrets": {
    "url": "neon-jdbc-url",
    "username": "neon-username",
    "password": "neon-password"
  },
  "secretVersion": "1"
}
```

This deploys Customer using only `neon-jdbc-url`, `neon-username` and `neon-password`, pinned to version `1`. Confirm that version exists and that the runtime account can access all three secrets. The JDBC secret should contain your Neon TLS JDBC connection URL. No JWT, RabbitMQ, VPC, other service URLs or other runtime accounts are required. Backend deployments still require `COLLECTOR_IMAGE` as an immutable digest.

The actual customer workflow is `.github/workflows/deploy-customer.yml`, displayed as **Deploy customer-service** in Actions; there is no `deploy-customer-service.yml` duplicate. It remains manual-only and uses WIF.

| Selected service | Required application secrets/configuration |
|---|---|
| Auth | Database + JWT |
| Customer, Bank | Database |
| Gateway adapter | None |
| Payment, Notification | Database + JWT + Rabbit host/user/password |
| API Gateway | JWT |
| Frontend | None |

Each secret-consuming deployment requires a numeric `secretVersion`; `latest` and `replace-` placeholders in consumed values are rejected. Optional Grafana export adds its token secret and pinned version requirement. Unrelated configuration is ignored. Shared accounts are permitted.

Dependency URLs are optional during configuration validation. Supplied dependency URLs are validated and rendered into routing/IAM audience settings. Configure all actual dependencies before testing business requests: Payment needs Customer/Gateway, Gateway needs Bank, API Gateway needs its routed backends, and Frontend needs API Gateway. Frontend's Nginx proxy requires its API Gateway URL to start successfully, so a frontend-only configuration can validate but cannot pass deployment health verification without that URL. Validation is not a claim that an incomplete application graph is operational.

The legacy full-stack validator still uses `deploy/cloud-run/config.full-stack.example.json` and its complete configuration contract. Do not pass the customer-only example to that legacy tool.

## Deployment order

Wait for existing CI to succeed on the exact main commit, then manually run:

| Order | Workflow | Cloud Run service |
|---|---|---|
| 1 | `deploy-auth.yml` | `auth-service` |
| 2 | `deploy-customer.yml` | `customer-service` |
| 3 | `deploy-bank.yml` | `mock-bank-service` |
| 4 | `deploy-gateway.yml` | `gateway-service` |
| 5 | `deploy-payment.yml` | `payment-service` |
| 6 | `deploy-notification.yml` | `notification-service` |
| 7 | `deploy-api-gateway.yml` | `api-gateway` |
| 8 | `deploy-frontend.yml` | `payment-frontend` |

For later changes, run only the affected service workflow. Changes to `cloud-runtime` require separately deploying its consumers (Payment, Gateway adapter and API Gateway). Coordinate schema compatibility and shared JWT changes across services; traffic rollback does not undo migrations or committed business work.

Each workflow checks CI, validates config, authenticates via WIF, builds/pushes one application, renders one manifest, preserves current traffic, checks a tagged candidate, promotes it and checks the canonical readiness endpoint. On failure after promotion, it restores that service's saved traffic allocation. Review `service-release-<service>-<run-id>` artifacts for the manifest, previous traffic and result. No other application's image, revision or traffic is modified. Health-token impersonation requires the runtime account's self-invoker permission. Existing Cloud Run services and canonical URLs must match the config.

`manual-cloud-run.yml` is now a validation-only legacy full-stack check; it cannot deploy all services. Existing `ci.yml` remains unchanged. The manual image-publishing workflow remains available for explicit full-stack image preparation and shared Collector releases, independently of deployment.

## Preserved production traffic during candidate creation

Both deployment scripts normalize live traffic before writing the replacement manifest. Explicit `revisionName` targets take precedence over `latestRevision` (including `latest_revision`); the latest flag is removed completely. Status-only URLs are omitted and tags/percentages remain. A latest-only target is pinned to the live `latestReadyRevisionName`; missing resolution fails locally. This ensures creating a candidate does not redirect production traffic to that candidate.

Before replacement, a local guard rejects conflicting targets, latest-based targets, changes to the saved production split, or any production allocation to the candidate revision. Candidate health is checked through its temporary tag before exact-revision promotion. Rollback uses the saved explicit revision allocation.

`deploy/cloud-run/customer-traffic.example.json` shows the exact transformation for illustrative revision `customer-service-00012-abc` (not a queried live revision). Before:

```json
{"traffic":[{"revisionName":"customer-service-00012-abc","latestRevision":true,"percent":100}]}
```

After, in `service.spec`:

```json
{"traffic":[{"percent":100,"revisionName":"customer-service-00012-abc"}]}
```

The deployment artifact's `customer-service.json` contains the actual rendered traffic and `previous-traffic.json` records the exact rollback split. No traffic setting is needed in the customer-only configuration example: traffic is read from the selected live service.

## Optional Cloud Build files

`cloudbuild-frontend.yaml`, `cloudbuild-auth.yaml`, `cloudbuild-customer.yaml`, `cloudbuild-payment.yaml`, `cloudbuild-gateway.yaml`, `cloudbuild-bank.yaml`, `cloudbuild-notification.yaml` and `cloudbuild-api-gateway.yaml` are build/publish alternatives, not triggers and not deployment jobs. Submit manually from the repository root, for example:

```sh
gcloud builds submit . --project=project-c9bd3d0e-266f-47bf-852 \
  --config=cloudbuild-auth.yaml --substitutions=_REVISION=$(git rev-parse HEAD)
```

The Cloud Build identity needs repository writer permission. No Cloud Build push trigger is created. GitHub workflows build with Docker on their hosted runner and do not require Cloud Build permissions.

Spring services use `${PORT:8080}`. Database settings use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Existing JWT consumers use `JWT_SECRET`; service URLs and RabbitMQ settings accept environment overrides. Compose explicitly sets the previous local ports. Cloud Run injects reserved `PORT`; it is omitted from manifest env values. Startup/liveness probes and the deployment's authenticated readiness check cover application startup. Business acceptance and cloud telemetry verification should follow the initial deployment.

Local validation covers deployment contracts, selection/rollback behavior, YAML and backend/frontend builds. Docker/Cloud Run execution must be verified by hosted CI and manually dispatched deployments on an authenticated environment; Docker is unavailable on this workstation.

References: [WIF through GitHub Actions](https://github.com/google-github-actions/auth#workload-identity-federation-through-a-service-account), [Cloud Run container startup order](https://docs.cloud.google.com/run/docs/configuring/services/containers), [Cloud Run health checks](https://docs.cloud.google.com/run/docs/configuring/healthchecks).
