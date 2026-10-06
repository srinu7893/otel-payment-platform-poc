# Service dashboards, PoC review and Cloud Run deployment steps

## Decision: ready to prepare an isolated cloud PoC, not yet deployed

The local application and telemetry paths have passing evidence. The last complete application run was manual E2E run 14 at source 921ae9548998e5cd5e81f045348131b3197561dc: 72 backend tests, 15 base scenarios, synthetic journey, three support lifecycle checks and nine screenshots. Final dashboard run 6 (37280134039) passed at source f586a6eaa255007b789ffc45178373afb16323c1: 143 query/coverage checks, provisioned panel validation and screenshots of all 13 dashboards, with no browser errors or diagnostics. Percentage ratios use an explicit 0–100% axis; uninitialized business snapshot timestamps are excluded from age charts. An absent age does not prove health: inspect refresh health. The exact dashboard archive SHA256 is 4001a0ba790d485459012858d614871c141eefd287539ac5e236fdc62e3ed0a1. A dashboard query being syntactically accepted does not prove it has useful data or that a business operation succeeded.

We can provision the isolated GCP environment and then deploy the applications. We do not need browser RUM, profiling, complete HA or every future dashboard before the first fake-payment cloud demonstration. We do need working storage, IAM, secrets, reachable service URLs and cloud business/signal acceptance. No live GCP/BigQuery/Grafana Cloud results exist in this session.

## Which dashboards to open

After starting the local stack, open Grafana at http://localhost:3001 . The local support UI exposes service links. The Payment POC folder contains 13 dashboards with 152 panels; the original four remain available.

| Dashboard | URL | What to inspect |
|---|---|---|
| Consolidated platform | http://localhost:3001/d/platform-overview | Compare service request rate, p95, 5xx, JVM resources, DB waiters and pipeline/business freshness |
| API Gateway | http://localhost:3001/d/service-api-gateway | Edge HTTP routes/errors/latency, JVM and downstream client calls |
| Auth | http://localhost:3001/d/service-auth-service | Login traffic/errors, JVM and database pool waits |
| Customer | http://localhost:3001/d/service-customer-service | Customer lookup traffic, JVM and DB saturation |
| Payment | http://localhost:3001/d/service-payment-service | HTTP/dependency latency, JVM/DB, durable outcome rows and delayed outbox work |
| Gateway adapter | http://localhost:3001/d/service-gateway-service | Adapter HTTP/client latency and JVM; use bank spans to identify timeout/error |
| Mock Bank | http://localhost:3001/d/service-mock-bank-service | Simulated bank HTTP latency/errors, JVM and DB waits |
| Notification | http://localhost:3001/d/service-notification-service | HTTP/JVM/DB, listener executions, durable delivery rows/age and snapshot freshness |
| Frontend coverage | http://localhost:3001/d/service-frontend | Current API synthetic journey and edge latency, with explicit missing browser/Nginx instrumentation |
| End-to-end operations | http://localhost:3001/d/payment-poc | Existing shared-service investigation and correlated logs |
| Business outcomes | http://localhost:3001/d/payment-business | Event trends plus durable-state/delivery panels |
| Telemetry pipeline | http://localhost:3001/d/telemetry-pipeline | Collector export queues/errors, outbox failures, broker backlog and warning state |
| Technical SLO | http://localhost:3001/d/payment-slo | Example HTTP availability, latency target and error-budget burn |

All seven backend dashboards include raw service logs, core HTTP/JVM metrics and links to Explore/all dashboards. DB pool panels appear only on DB-backed services; downstream client panels appear on callers. Empty GC/client/queue/alert series can be legitimate when no observations exist. Missing core HTTP/JVM metrics are a coverage failure, not a green service.

Rate charts include readiness probes; inspect route breakdowns to understand business traffic. Memory panels select OTel JVM pool labels to avoid combining overlapping Micrometer memory instruments. Reporting-instance counts indicate telemetry presence, not readiness. Current DB-backed business gauges must use max across replicas sharing the same DB, not sum. Cloud series names/resource labels need live validation before importing the same queries.

Frontend has no Java agent and no browser OTel SDK/Nginx exporter yet. Its dashboard explicitly shows API journey visibility only. Synthetic duration does not measure page load or rendering. Do not report frontend CPU, web vitals or browser error rate from backend metrics.

## How to regenerate and test dashboards

1. Run python3 scripts/dashboards/generate.py after editing its service/panel definitions. Commit the generator and generated JSON together.
2. Start the isolated stack with bash scripts/run-poc.sh start . Grafana file provisioning loads the JSON automatically. Generate traffic and allow export/scrape time.
3. Run python3 scripts/e2e/dashboard-review.py against the running stack. It checks provisioning/panel counts, runs every Prometheus query and requires HTTP/memory/CPU/thread data per Java service. It retains observed resource/label mappings for migration review.
4. Use the separate manual-dashboard-review workflow for a clean packaged stack, business/synthetic traffic, query checks and screenshots of all dashboards. Before merge, manually apply run-dashboard-review to the same-repository PR. On the default branch, use Actions → Run workflow. Ordinary CI does not start this stack or run browser/E2E checks.
5. Review artifacts/dashboard-review.json, dashboard-metric-labels.json, dashboard-browser.json and service-dashboards/*.png. Check actual data and empty-series interpretation as well as PASS labels.

## Review findings: gates and gaps

This review covers application configuration/security boundaries, durable/asynchronous flow, instrumentation/export/storage, service dashboards, alerting, tests and Cloud Run release assets. It is a source/evidence review, not an independent security audit or production certification.

| Priority | Finding / evidence | Required next action |
|---|---|---|
| Before cloud release | Available config is config.example.json with placeholders; concrete accounts/project/network/storage and cloud evidence are not supplied | Select project/region and provision dependencies; replace placeholders with exact non-secret resource references |
| Before cloud release | Release deliberately updates existing eight service slots; it cannot create the environment | Bootstrap slots and capture canonical URLs before release preflight |
| Before cloud release | JDBC uses ordinary PostgreSQL; no Cloud SQL JDBC connector or DB CA mount exists | Choose and implement a verified connection method for the actual database. Cloud SQL private IP still needs routing and a supported TLS/trust design; do not assume an arbitrary JDBC URL works |
| Before cloud release | RabbitMQ pull consumers and scheduled outbox/reconciliation depend on background execution | Keep configured instance-based CPU/minimum instances for the isolated demo; validate reconnect/catch-up in cloud. Budget for always-running workers |
| Before cloud release | WIF/deployer/runtime IAM, Secret Manager values and service caller permissions are account-specific | Configure least-privilege identities, private/public policies and exact-commit gates; prove denied unauthenticated private calls |
| Before cloud acceptance | Native exporters/optional Grafana mapping are syntax-tested, not live-tested | Prove Cloud Logging trace linkage, Cloud Trace spans and Managed Prometheus metrics for actual service/version resources |
| Before broad availability | Public frontend/API, seeded demo users/default passwords and demo JWT defaults are local/demo conveniences | Use an isolated fake-data environment, strong unique cloud secrets and controlled test audience; design rate limiting/SSO before broad public use |
| Before production | Dependency/container vulnerability scans and SBOM/attestation are not recorded; demo auth is not production identity management | Scan pinned dependencies/images, define token/SSO/access policy and verify findings before broad use |
| Before production | Max one instance is per revision; candidate/old revisions can run concurrent workers | Test distributed scheduling/leases, consumer dedupe, compatible migrations and background side effects; HTTP rollback does not undo them |
| Before production | Cloud Collector sidecar queues are bounded and ephemeral; local disk-queue recovery does not cover instance loss | Define telemetry loss budget, durable gateway/HA strategy and longer outage/load/DR tests |
| Before production | Secret-pattern redaction covers selected OTLP fields/bodies; console output and all PII are not covered | Structured allowlists/redaction, privacy tests across paths, retention/RBAC and access review |
| Before production | Inbox is local/no production authentication; alerts route to a PoC owner, not an actual pager | Select secure alert channel, real owner/escalation and verify firing/resolution/silence/notification failure behavior |
| Next monitoring increment | Durable merchant payment rows are not a windowed success SLI; transfer/refund counts and true payment-to-delivery latency are absent | Add durable domain SLIs and targets, bounded dimensions and database truth checks |
| Next monitoring increment | Frontend RUM/Nginx metrics, profiles, host/container saturation and complete DB diagnostics are absent | Add features against concrete investigation needs; never fabricate unavailable data |
| Scale/performance | Scheduled snapshots scan tables; JDBC pool waits are measured but query/external-provider root cause still needs traces/infra evidence | Statement timeouts, indexes/incremental summaries, representative load/capacity tests and query plans |
| Deployment lifecycle | CI image archive reuse, dependency/base-image attestation, full bootstrap automation and exact-main cloud acceptance remain incomplete | Treat as release/infrastructure improvements; keep manual evidence bound to exact tested source |
| Scope | Bank, email/SMS and money movement are simulated | Use approved provider sandboxes and provider-specific safety/contract tests before real integrations |

The current local tests prove useful selected failures and recovery. They do not prove every error, exception, payload, concurrent worker race, prolonged outage or cloud provider condition. No measured MTTR percentage improvement has been established.

## Cloud Run: step by step

### Step 1 — choose the deployment inputs

Provide the GCP project ID, region, VPC/subnet, database choice and reachable TLS RabbitMQ endpoint. Start with native Cloud Logging/Cloud Trace/Managed Prometheus. Add Grafana Cloud and BigQuery after native ingestion works. Use a dedicated fake-data project/environment and budget alerts.

Deploy eight HTTP services: seven Java microservices plus frontend. Java services each contain an app and Collector sidecar. PostgreSQL/RabbitMQ are durable external dependencies. Do not deploy local Prometheus/Tempo/Loki/Grafana as ephemeral Cloud Run storage containers. Local Alertmanager/inbox are not included in the current cloud manifests.

### Step 2 — enable APIs and create the image repository

From authenticated Cloud Shell, set PROJECT_ID and REGION to the chosen values. Do not paste service-account keys into the repository. Use gcloud services enable for run.googleapis.com, artifactregistry.googleapis.com, iamcredentials.googleapis.com, secretmanager.googleapis.com, logging.googleapis.com, cloudtrace.googleapis.com and monitoring.googleapis.com; also enable the chosen SQL/Compute/network APIs. Create a Docker Artifact Registry repository named otel-payment-poc in that region.

Confirm billing, organization policy and your provisioning permissions. Workload release permissions are distinct from infrastructure provisioning permissions. The existing SRE repo's WIF settings are a reference, not credentials automatically available in this repo.

### Step 3 — create durable storage and network connectivity

Provision PostgreSQL with backups/access policy and RabbitMQ with TLS, durable queues and restricted access. Ensure database and broker are reachable from the configured VPC/subnet. Decide the database TLS/connector method before preparing its JDBC secret. Current code does not install a Cloud SQL connector/CA mount, so that integration requires its own change/test if Cloud SQL is selected.

Use only the seeded fake accounts for this PoC; confirm migrations/seed state. All services use the configured database with separate logical schemas, not seven separate database servers. Keep RabbitMQ credentials/host and database values separate from public frontend configuration.

### Step 4 — create identities and secrets

Create eight distinct runtime service accounts and a deployment identity. Java runtime identities need Logging writer, Trace agent and Monitoring metric-writer roles for exports. Grant Secret Manager access only to required secrets. Frontend gets no database/JWT/OTLP secret.

Create Secret Manager versions for the JDBC URL/user/password, RabbitMQ user/password and shared demo JWT signing secret. Use pinned numeric versions. Do not reuse repository/local default values. See CLOUD_RUN_PLAN.md for service-specific requirements and health impersonation permissions.

### Step 5 — bootstrap the eight service slots and record URLs

This is an initial provisioning task, not handled by run_release.py. Create the eight named Cloud Run services with their runtime identities, initially authenticated/private, using a vetted bootstrap image. These temporary slots supply canonical URLs and prior traffic allocation. They are not working payment services. Use the intended prefix, region and service names from config.example.json.

Record each stable service URL. After candidate application revisions deploy, validate actual readiness/business behavior; rolling back the first deployment to bootstrap revisions does not restore a working old PoC. Do not call the first release successful until cloud acceptance passes.

### Step 6 — apply the caller IAM graph

API Gateway may invoke all six internal services for support health. Payment invokes Customer and Gateway adapter; Gateway adapter invokes Mock Bank. Add target runtime self-invoker for the release's authenticated health check. Frontend and API Gateway are the two public endpoints in this design; all other services must reject anonymous invocation. Apply organization/test-access constraints appropriately.

Google workload ID tokens use the target canonical origin as audience and X-Serverless-Authorization; customer JWT remains in Authorization. IAM-protected backends currently use ingress=all. A different internal-ingress topology needs separate network testing.

### Step 7 — complete GitHub configuration

Copy deploy/cloud-run/config.example.json and replace every placeholder with project/region/registry/network/subnet, all eight service URLs/accounts, required secret names and numeric secret version. Do not commit credentials or secret values.

In this repository create environment gcp-poc. Configure secrets WIF_PROVIDER and GCP_SERVICE_ACCOUNT; set variable CLOUD_RUN_CONFIG_JSON to the concrete non-secret JSON. Restrict WIF trust to this repo/intended branch/environment and identity. For cloud acceptance configure TEST_CUSTOMER_USERNAME, TEST_CUSTOMER_PASSWORD and TEST_ACCOUNT_NUMBER for the isolated fixture and necessary telemetry reader permissions. Optional GRAFANA_URL is a UI URL, not the Collector ingestion endpoint.

### Step 8 — validate the approved main revision

After the PR is reviewed/merged, run normal CI and manual-otel-e2e on the exact main commit. Run manual-dashboard-review on that commit for current dashboard evidence. Existing PR-run evidence does not satisfy a different merge commit's exact-source cloud gate.

Run manual-cloud-run with action validate first. Inspect contract/config/container validation and rendered manifests. Local validation can also use python3 scripts/cloud/run_release.py validate --config PATH_TO_CONCRETE_CONFIG ; allow-example validates placeholders only and must never be treated as deployment readiness.

### Step 9 — run the manual release

Run manual-cloud-run with action deploy on main. It builds nine images, verifies source labels/digests, validates IAM/URLs, creates candidate revisions, verifies readiness, promotes exact revisions and saves traffic allocations/rollback evidence. It does not run automatically on builds.

Monitor deployment evidence. Failed readiness triggers traffic rollback where possible; migrations, payments and processed background work are not reversed. If a rollback fails, use the saved pre-release allocation and reconcile affected work.

### Step 10 — verify applications and native telemetry

Run manual-cloud-acceptance against the isolated environment. Confirm all service readiness, anonymous private-service rejection, missing/insufficient JWT rejection, fake payment completion, safe replay, notification delivery, linked cloud logs/traces and a Managed Prometheus application series. Inspect actual resource identities and expected labels. Verify background catch-up and cloud queue/export behavior before extending the outage claims.

Only then call the cloud PoC deployed and working. The source/config validation already completed locally is not this proof.

### Step 11 — refine cloud dashboards and observability

Start with native Cloud Logging/Trace/Monitoring UI. If using Grafana, configure an authenticated Managed Prometheus datasource or the prepared Grafana Cloud OTLP fan-out with account-specific credentials. Import the service/overview dashboards only after verifying cloud metric names/labels; remap local datasource UIDs, links and service selectors. Local Loki log panels do not automatically query Cloud Logging.

Apply alert/recording rules in the selected cloud monitoring system; local Prometheus rules are not automatically uploaded. Verify real alert delivery. Add BigQuery linked-log analytics after bucket/link permissions and schema/query/cost validation. Add RUM, profiles and other features after the foundational cloud data path is proven.

## Official references

- https://docs.cloud.google.com/run/docs/deploying
- https://docs.cloud.google.com/run/docs/authenticating/service-to-service
- https://docs.cloud.google.com/sql/docs/postgres/connect-run
- https://docs.cloud.google.com/stackdriver/docs/instrumentation/opentelemetry-collector-cloud-run
- https://docs.cloud.google.com/stackdriver/docs/managed-prometheus/query
- https://docs.cloud.google.com/logging/docs/analyze/query-linked-dataset
