# Cloud Run deployment and enterprise observability

This plan builds on the manual WIF/digest/readiness/promotion pattern in `srinu7893/Sre-Assessment/.github/workflows/deploy.yml` and `scripts/ci/cloud_run_release.py`. That private reference repo was read, not modified. Its project credentials and trust policy are not copied to this repository. Cloud Run release code is implemented here; live GCP execution has not been established by local tests.

## What deploys separately

| Component | Deployment | Reason |
|---|---|---|
| frontend | Own Cloud Run service | Static React bundle and Nginx HTTPS proxy to the public API edge; no application secrets or Collector |
| api-gateway | Own Cloud Run service plus Collector sidecar | Public HTTPS edge, customer JWT/roles; workload ID tokens on private backend calls |
| auth, customer, payment, gateway, mock-bank, notification | Six independent Cloud Run services, each with a Collector sidecar | Separate revision, runtime service account, resources and observability identity; backends require IAM invocation |
| payment scheduler/outbox | Initially inside payment service | Instance-based CPU and minimum one instance keep the existing scheduled work running; database row locks protect outbox claims |
| notification RabbitMQ listener | Initially inside notification service | Minimum one instance and always-allocated CPU keep pull consumption running |
| PostgreSQL | Cloud SQL PostgreSQL or an explicitly managed durable equivalent | Flyway schemas remain service-specific in the shared PoC database; backups/PITR and connection limits need configuration |
| RabbitMQ | Managed RabbitMQ or durable private VM/GKE deployment | Retains current AMQP/retry/DLQ contract; TLS 5671 required by cloud manifests |
| Traces, logs and metrics | Cloud Trace, Cloud Logging, Managed Service for Prometheus | Sidecar uses attached runtime identity to export; storage is managed outside app instances |
| Grafana/Tempo/Loki/Prometheus OSS | Existing local demo; optional managed or durable GKE/VM environment later | Never place their only database/files on an ephemeral Cloud Run filesystem |
| BigQuery | Optional linked dataset on an analytics-enabled Cloud Logging bucket | Historical SQL analysis and joins; not payment transaction storage or a trace waterfall replacement |

Seven Java services plus one frontend means **eight independently deployable HTTP services**, not a single Docker Compose deployment. Each Java service's Collector shares its network namespace, receives OTLP at `127.0.0.1:4318`, enriches GCP resources, removes selected sensitive attributes/body patterns, batches and exports. It is not a central HA Collector and has only bounded in-memory queues. Cloud Run instance replacement can lose buffered telemetry. The local file-backed queue recovery result cannot be applied to this sidecar.

For the first PoC, revisions use a low concurrency of 20, a revision maximum of one instance, and five JDBC connections per DB-backed instance. Revision overlaps during candidate verification/rollout still permit multiple schedulers/consumers. This is not a production single-worker guarantee. For scale, split outbox/reconciliation and notification workers from HTTP APIs, introduce tested distributed scheduling/ownership, and evaluate Cloud Run worker pools or Pub/Sub push plus Cloud Tasks/Scheduler. Changing AMQP to Pub/Sub also changes delivery, retry/DLQ and propagation semantics and requires new tests; it is not a YAML substitution.

## Workload identity and secrets

Application `Authorization: Bearer <customer JWT>` is preserved. The Cloud Run caller supplies `X-Serverless-Authorization: Bearer <Google ID token>` with the target's canonical service origin as audience. Tokens come from the fixed metadata endpoint, are checked for audience/lifetime and cached with a 60-second refresh margin. A fixed deployment-configured audience allowlist prevents sending workload credentials to an arbitrary destination. Reactive gateway/probe token work runs outside the Netty event loop. Caller-provided workload headers are replaced. Local IAM mode is disabled.

Each target needs `roles/run.invoker` for only its actual callers. The gateway calls all six services for support health probes; ordinary routes call auth/customer/payment/notification. Payment calls gateway/customer; gateway calls bank. Private health verification impersonates the target runtime service account, so that account must also have self-invoker permission. Only frontend and api-gateway have `allUsers` invoker. IAM-protected backends retain `ingress=all` for canonical service-to-service HTTPS; an internal-ingress design requires separately validated VPC routing and is not claimed here.

Runtime Java service accounts require `roles/cloudtrace.agent`, `roles/logging.logWriter` and `roles/monitoring.metricWriter`. Grant Secret Manager access only to each service's required secret versions; frontend needs none. Deployment identity requires Artifact Registry write, Cloud Run release permissions, actAs on runtime accounts, and the narrowly scoped impersonation permission used for health checks. WIF trust must restrict this repository, intended branch/environment and deployment identity. Enable Run, Artifact Registry, IAM Credentials, Secret Manager, Logging, Trace and Monitoring APIs. Cloud SQL/VPC resources and access require separate provisioning.

`config.example.json` contains names and URLs, not secret values. JDBC URL, DB username/password, Rabbit username/password and JWT secret are referenced from pinned numeric Secret Manager versions. The JDBC URL must use a correctly configured encrypted/verified database connection; this increment does not install a Cloud SQL JDBC connector or CA mount. Rabbit TLS trust must match the managed provider's certificate. Neither private networking nor TLS alone establishes a complete production security review. Seeded demo users/bank accounts are for isolated demonstrations, never a real payment service.

## Recreate and release

1. Provision the isolated GCP PoC project, Artifact Registry repository, VPC/subnet, durable PostgreSQL/RabbitMQ, per-service runtime accounts, secrets and eight initial Cloud Run service slots. First creation/bootstrap is a separate provisioning step; this release script deliberately updates **existing** services and requires their canonical URLs. Do not claim it provisions the whole project.
2. Copy `deploy/cloud-run/config.example.json`, replace every placeholder, select a region suitable for users/data and fill in the actual service URLs and secret names/versions. Database/broker must be reachable through the configured VPC. Assign the IAM graph above and enable exporter APIs.
3. Create this repo's GitHub environment `gcp-poc`. Configure secrets `WIF_PROVIDER` and `GCP_SERVICE_ACCOUNT`; environment variable `CLOUD_RUN_CONFIG_JSON` is the non-secret config JSON. For manual cloud acceptance, also set `TEST_CUSTOMER_USERNAME`, `TEST_CUSTOMER_PASSWORD`, and `TEST_ACCOUNT_NUMBER` secrets for an isolated seeded demo account, and grant the test identity Logging/Trace/Monitoring read permissions. Credentials from the SRE assessment repository do not automatically exist here.
4. Merge the reviewed implementation when approved. Run normal CI and **manual-otel-e2e** on the exact main commit. Then Actions → **manual-cloud-run** → `validate`, or `deploy` when the environment is ready. Deployment only permits main. Neither cloud workflow nor full E2E runs automatically on pushes.
5. The workflow rebuilds/tests the same source, verifies the pinned Java agent, builds nine images (eight apps plus Collector), attaches the source revision label, pushes and resolves immutable digests. This follows the SRE safeguards but currently rebuilds rather than downloading CI container archives. A reproducible dependency/base-image attestation pipeline remains future work.
6. Release validates configured URLs and private/public IAM policies before mutation. It preserves old traffic, creates a tagged candidate, checks private readiness with a canonical-audience ID token, promotes the exact revision, checks traffic convergence and readiness again. On failure, promoted services restore their saved traffic allocations. Evidence contains digests, rendered manifests, revision URLs and pre-release allocation.
7. Run **manual-cloud-acceptance** separately. It verifies public and private auth failures, a small uniquely keyed simulated payment plus notification, Cloud Logging trace correlation, Cloud Trace spans and a Managed Prometheus application series. Cloud tests mutate only the isolated demo's fake transactions. Inspect actual cloud evidence before declaring integration complete. A successful deploy readiness check is not full business or telemetry acceptance.

Automatic rollback only changes HTTP traffic; it does not roll back Flyway migrations, committed payments or background work already processed by a candidate. Use compatible database migrations and inspect queued work. Initial creation, DR/backups, cross-revision worker safety, cloud fault injection, IdP/SSO, pager delivery and benchmark/HA certification remain separately required for production.

## Where support looks

| Question | Local view | Cloud view |
|---|---|---|
| Raw application event | Grafana Explore → Loki `{service_name="payment-service"}` | Cloud Logging filter `logName="projects/PROJECT_ID/logs/payment-poc-otel"`; inspect textPayload/jsonPayload plus resource, severity, trace and spanId |
| Exact payment | Loki `|= "PAYMENT_ID"` | Same Cloud Logging logName plus `SEARCH("PAYMENT_ID")`; include a time range |
| One execution | Loki structured `trace_id` → Tempo waterfall | Cloud Logging `trace="projects/PROJECT_ID/traces/TRACE_ID"` → Cloud Trace waterfall |
| System-wide scope | PromQL HTTP rate/error/latency/JVM, business logs | Managed Prometheus/Cloud Monitoring plus Cloud Run request/instance/CPU and Cloud SQL/Rabbit metrics |
| Why payment completed but no message | Outbox log + publish attempts + Rabbit depth/consumer/DLQ | The same business IDs and spans; configure durable broker monitoring separately rather than assuming sidecar app metrics include it |
| Historical reports | Loki range queries within local retention | Linked BigQuery SQL, retention/permissions and query budgets |

Cloud Run also automatically collects application stdout and request/system logs. OTLP-exported application events use a distinct `payment-poc-otel` log name. Do not sum both paths as unique business outcomes: duplicates can occur. The Collector body-redaction policy protects its own exported path, not application stdout. Never log credentials in the first place. Linked datasets are read-only; do not target one with an ordinary BigQuery export sink.

The new `payment-slo` dashboard uses unsampled HTTP server metrics at the edge. Its example technical availability target is 99.9%; 5xx count as failures. It shows a latency-good ratio <=2.5 seconds and fast burn when both 1h/5m windows exceed 14.4 times the 0.1% error budget. No traffic gives no meaningful SLI, rather than proving 100% health. HTTP 200 with RECONCILIATION_REQUIRED remains a business failure: business completion/delivery SLIs require an independent, durable source and approved domain targets. These SLO targets are PoC examples, not an agreed production SLA. Local Prometheus warnings now route through Alertmanager to a local incident inbox; production pager delivery is still unconfigured. Local rules/dashboard are not automatically uploaded as Cloud Monitoring policies.

## Production support triage, from report to verification

1. Record the affected business ID, UTC start/end window, customer-visible outcome, correlation/trace ID and deployed source version. Preserve idempotency keys for reconciliation. Collect only the minimum permitted synthetic/business identifiers; never paste tokens, credentials or full sensitive payloads into an incident ticket.
2. Establish impact. Check ingress request rate/5xx/latency, technical SLO windows and Cloud Run capacity/cold starts. Then check business completion/reconciliation logs and notification backlog/DLQ. A green HTTP availability chart cannot establish that money movement or eventual delivery succeeded.
3. Find the event by business ID, then the exact trace. Inspect server/client/JDBC/outbox/consumer parent relationships and elapsed time. Follow the critical path; overlapping spans cannot simply be added. A successful span or HTTP 200 can coexist with a declined/unknown business result. Trace sampling and async export mean missing spans are not proof that work never occurred.
4. Separate application trouble from telemetry trouble. If data is absent, check receiver accepted/refused counts, export errors, queue occupancy/capacity, backend availability, scrape/auth permissions, time range and sampling. Local `collector-ingress.json`, `collector-queues.json`, Prometheus targets and sidecar/cloud logs provide distinct evidence. Sidecar readiness only proves its health endpoint responds.
5. Choose a bounded action. For broker trouble, restore broker connectivity and verify the persisted outbox catches up with duplicate-safe notification delivery. For an unknown bank outcome, reconcile the original operation; do not create a fresh financial retry ID. For poison events, inspect DLQ cause/ownership and redrive deliberately. For a bad release, restore the saved traffic split, then separately assess migrations and background work already performed.
6. Verify recovery with a uniquely keyed small fake payment, completed domain status, eventual SENT notification, stored correlated logs, a distributed trace, exported metrics and a draining backlog. Check both the particular transaction and aggregate trend windows. Capture actual results before closing the incident; a service restart alone is not a recovery test.
7. Produce an incident record: impact/window, source revision, symptom, evidence/query links, suspected versus confirmed cause, action, verification and follow-up owner. Preserve the guide/evidence against the tested revision and revisit alert thresholds/privacy/sampling when behavior changes. Pager escalation, incident ownership and production access controls require the team's own configuration.

Useful scenario mapping: bank latency → waterfall plus timeout/reconciliation event; bank 500 → downstream error span plus UNKNOWN outcome; broker outage → outbox failure attempt/queue depth then catch-up; consumer poison payload → bounded retries and DLQ; Collector/backend outage → business path still runs but telemetry queues/errors identify the observation failure; wrong customer/role → expected 401/403 without an unauthorized business change. The local automated acceptance covers these named paths at its recorded scope. Cloud fault-injection, real provider outages and recovery at production scale remain separate tests.

## Keep Grafana in the cloud demonstration

The native GCP export path provides Cloud Logging/Trace/Monitoring UIs. The cloud frontend build gets the project ID and links its support panel to those UIs. If you set the environment variable `GRAFANA_URL` to your validated HTTPS Grafana UI URL, the cloud build instead uses Grafana dashboard/query links; this is different from the Collector OTLP ingestion endpoint. It does not automatically make a cloud Grafana instance or preserve the local Loki/Tempo datasource links. Two concrete options are available:

- Query Managed Prometheus from an independently hosted Grafana using Google's documented authenticated datasource/token refresh setup; recheck series names/labels and import the applicable dashboards. Local Prometheus recording rules must be evaluated separately or implemented as cloud policies.
- Optionally add `grafana` to the concrete deployment config with `endpoint` (the account's HTTPS `.grafana.net/otlp` gateway), numeric `username` (instance ID), and `tokenSecret` (Secret Manager secret name, same pinned version). The baked `collector-grafana.yaml` overlay fans out traces/logs/metrics to both native GCP and Grafana Cloud. It references a backend token only in the Collector container. Grant the runtime accounts that secret's access and token write permissions for only the required signals. Validate the actual account endpoint/retention/rate limits, provision datasource UIDs and trace/log links, import dashboards and test backend queries before claiming live Grafana integration. Dual export increases cost and backend failure/loss handling needs observation; sidecar queues remain ephemeral.

The optional overlay is syntax checked with fake credentials in the manual local workflow, never sent to a real account in that check. Grafana Cloud live ingestion and UI mapping still require an account and manual acceptance. See https://grafana.com/docs/opentelemetry/ingest/ and https://docs.cloud.google.com/stackdriver/docs/managed-prometheus/query .

## BigQuery: three useful queries

Use `deploy/bigquery/queries.sql`. Replace `PROJECT_ID.LINKED_DATASET._AllLogs` with the actual linked view. The queries use the **linked Cloud Logging schema** (`text_payload`, `json_payload`, `span_id`, `log_name`); a direct Logging → BigQuery sink has different field/schema behavior and must not reuse these blindly. Queries cover bounded raw log search, ordered logs for a trace, and hourly event counts. Event log counts are emitted events, not guaranteed unique transactions. Keep time predicates, LIMIT/query cost controls, narrow log names and use insert_id/business IDs when deduplication matters.

Recommended first integration: OTLP logs → Cloud Logging analytics-enabled log bucket → linked BigQuery dataset. Logging retains the data while BigQuery can read/join it, avoiding a second streaming copy by default. Add business reporting tables or CDC separately with privacy/access controls. BigQuery is useful for 30/90-day outcome trends and joining releases/incidents/business data. Tempo/Cloud Trace remains the primary span waterfall UI; Prometheus/Monitoring remains the operational metric/alerting system.

## Feature catalogue and boundaries

| Capability | Status in this PoC | Next production step |
|---|---|---|
| Trace/log/metric instrumentation; W3C HTTP/JDBC/messaging propagation | Implemented and local acceptance tested | Live cloud signal mapping and resource/instance isolation |
| Explicit business spans, durable outbox parent context, bounded attempt metrics | Implemented | Validate rolling revisions, leases and sustained broker failures |
| Log correlation/raw queries, service graph, span metrics, four dashboards | Implemented locally | Cloud/Grafana datasource auth and equivalent field/series mapping |
| Head sampling | Local always-on; Cloud manifest parent-based ratio 1.0 | Agree sampling/retention and verify parent decisions |
| Tail sampling | Separate error/slow/normal experiment | Trace-aware routing, capacity and late async spans; not a per-service sidecar strategy |
| Memory/batch/resource/privacy processors, retry and queue self-observation | Implemented | Loss budgets, rate limits and export authentication validation |
| Local persistent export queues | Crash/recovery test | Cloud sidecar queue is ephemeral; use a durable central gateway if needed |
| Baggage | Propagator configured, not a tested business baggage feature | Allowlist safe fields; never put credentials/PII in baggage; no durable baggage persistence |
| Secret-pattern body redaction + selected header/DB attribute deletion | Implemented with synthetic stored-log test | Structured fields, payload paths, stdout and privacy review; regex is not exhaustive |
| Technical SLO/burn-rate alerts | Rules/dashboard, deterministic rule tests and local inbox routing | Approved domain objectives, production load/windows and pager routing |
| Cloud IAM, Secret Manager references, Collector managed exporters | Runtime/release assets implemented | Actual accounts, permissions, storage and live manual acceptance |
| BigQuery linked analytics | SQL/runbook prepared | Create analytics bucket/link, access, retention and live schema/query validation |
| Exemplars | Datasource destination configured | Verify actual emitted exemplars and click-through on selected metric/backend |
| Browser RUM, browser-to-server propagation | Not implemented; browser functional tests are not RUM | Privacy/consent, safe ingestion proxy and sampling; never expose OTLP credentials in React |
| Continuous profiling/eBPF, Pyroscope | Not implemented | Separate evaluated collector/agent/backend integration; OTel profiles spec is under development |
| Kubernetes host/container/network telemetry, database/broker infrastructure telemetry | Rabbit metrics local; no GKE cluster/host coverage | Cloud provider metrics or secured infra agents/receivers for the selected platform |
| OpAMP/fleet remote management, connector routing/multi-tenant gateways | Not implemented | Add only with a concrete operational requirement and validated trust model |
| Collector connectors/transforms, multiple backend routing | Selected transforms and Tempo-generated span/service-graph metrics | Avoid duplicate span metrics; validate each additional component/version/backend |
| HA, DR, load, chaos, upgrade and multi-replica guarantees | Not certified | Dedicated capacity/DR/security tests and operational ownership |

OpenTelemetry has an extensible ecosystem, not a finite switch labelled “all features.” This PoC implements core investigation and useful advanced support paths. A clear capability/verification boundary makes it more credible than adding every receiver/exporter without a purpose.

## Official references

- https://docs.cloud.google.com/run/docs/authenticating/service-to-service
- https://docs.cloud.google.com/run/docs/container-contract
- https://docs.cloud.google.com/run/docs/configuring/billing-settings
- https://docs.cloud.google.com/run/docs/deploy-worker-pools
- https://docs.cloud.google.com/stackdriver/docs/instrumentation/opentelemetry-collector-cloud-run
- https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.136.0/exporter/googlecloudexporter
- https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.136.0/exporter/googlemanagedprometheusexporter
- https://docs.cloud.google.com/logging/docs/analyze/query-linked-dataset
- https://docs.cloud.google.com/logging/docs/analyze/examples
- https://opentelemetry.io/docs/specs/status/


## Local support enhancements and cloud boundary

SUPPORT_ENHANCEMENTS.md describes new durable-state gauges, freshness checks, on-demand synthetic journey and local Alertmanager/inbox lifecycle. Their local acceptance does not certify cloud alert delivery. Deploy the metrics with the Java services, validate backend label/name mapping, and implement rules/routing in the chosen managed monitoring platform. The local inbox/Alertmanager are not Cloud Run services in this release.
