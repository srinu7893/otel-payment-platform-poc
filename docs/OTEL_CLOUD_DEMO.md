# Cloud OTel investigation demo

This increment connects a request to its trace/log evidence and provides Cloud Monitoring dashboards for services and API operations. Application correctness tests and stored telemetry acceptance have separate results. A response trace ID proves active instrumentation, not successful export/storage.

## Run and inspect a request

Import either Postman collection with its matching cloud environment. Send a request, then open **Visualize** for **Open distributed trace** and **Open correlated logs**. Collection scripts generate a W3C trace parent and assert that `X-Trace-Id` matches the accepted request trace. `X-Correlation-Id` is bounded and available even on application security rejections. Without active instrumentation the gateway omits the trace header; it never invents a trace ID from an untrusted header.

The environment receives `last_trace_id`, `last_correlation_id`, `last_trace_url` and `last_logs_url`. A payment's first observed creation trace is also retained as `otel_payment_trace_id`, `otel_payment_trace_url` and `otel_payment_logs_url`; an idempotent replay of that same payment does not replace the original stored link. These links require your Google Cloud viewing identity. Login JWTs are never embedded in URLs.

In the frontend, sign in as **support / support123** or **admin / admin123**. Open **Observability > Recent API request traces**. Refresh operations or perform a lookup to populate the table. The table holds at most 10 requests in memory, with route templates, HTTP status and diagnostic IDs. It excludes request bodies, tokens and concrete customer/account/transaction IDs from operation names. Click **Logs** or **Trace**. The manual trace-ID search now opens the trace directly.

In the trace waterfall, inspect individual span start times/durations and parent/child relationships. Identify which downstream service consumed time, then inspect its logs. Async notification work can extend after the original HTTP response; do not sum overlapping span durations as customer wall-clock latency. A business decline or reconciliation-required outcome needs investigation even if its HTTP response was 200/201.

## Cloud dashboards

Definitions are under `deploy/cloud-run/dashboards/`: an overview and seven Java-service dashboards. They use the Managed Prometheus job convention `payment-poc/<service>`, rather than assuming the local Collector's `service_name` label exists in cloud metrics.

Each dashboard includes request rate, API operation traffic/count, 4xx/5xx rates, p50/p95/p99 latency, technical availability and JVM memory. Route templates and HTTP methods identify API operations. Actuator routes are excluded. Zero traffic leaves availability undefined; it is not replaced with 100%. Counts use Prometheus counter estimates, not bank ledger audit counts. Missing metrics must be investigated, rather than replaced with zeros. Browser RUM is still development-only and is not claimed by these Java-service dashboards.

Run **GitHub Actions > OTel Cloud dashboards > Run workflow**:

- `apply=false`, `verify_queries=false`: render definitions only; no Cloud API writes.
- `apply=true`: create/update the eight named POC dashboards. Existing dashboards with other names are untouched; duplicate matching names are rejected. Updates use etags.
- `verify_queries=true`: evaluate each panel over the previous hour at 60-second steps, matching a historical chart rather than checking only the current instant. Empty or NaN/Inf-only results are NO_DATA and fail verification. Sparse traffic or missing JVM metrics require investigation. Finite zero error rates are shown only when a matching observed traffic series exists.

Run sandbox acceptance first, then panel verification, so there is recent traffic to inspect. A successful series-presence check for seven services does not prove every panel or latency histogram has data. The dashboard artifact records total and finite sample counts per panel to distinguish undefined results from observed values. Range query behavior is documented in the [Cloud Monitoring API](https://docs.cloud.google.com/monitoring/api/ref_v3/rest/v1/projects.location.prometheus.api.v1/query_range).

Access dashboards at https://console.cloud.google.com/monitoring/dashboards?project=project-c9bd3d0e-266f-47bf-852 . Published resource names and direct links are saved in the workflow's `otel-cloud-dashboards-*` artifact. Rendered files alone do not establish that dashboards were installed or populated.

## Stored telemetry acceptance

Run **GitHub Actions > manual-cloud-acceptance > Run workflow**. This is manually invoked and uses WIF. It discovers the canonical API gateway URL; it does not require configuration/secrets/service accounts for unrelated services. Existing TEST_CUSTOMER_USERNAME/PASSWORD secrets can select a test customer; otherwise it uses the public sandbox demo credentials. The wallet must have at least INR 1 of test funds. It creates one INR 1 sandbox payment and safely replays its UUID idempotency key. It does not add funds automatically or trigger a deliberate outage.

The suite checks:

1. Anonymous API rejection and customer denial on support operations.
2. Completed payment, the actual response trace ID, delivered notification and duplicate-safe replay.
3. A stored PAYMENT_COMPLETED log correlated to that trace.
4. A connected trace spanning API gateway, payment, customer, gateway, bank and notification, with DB instrumentation and durable `outbox.publish` span.
5. Stored HTTP metric series for all seven Java services.

Artifact `cloud-acceptance-*` contains individual PASS/FAIL/BLOCKED results, transaction diagnostic IDs, safe log counts/timestamps, safe span relationships/durations and service metric coverage. It does not upload JWTs, raw log payloads, complete span attributes or request bodies. A missing signal or disconnected service fails. Cloud API 401/403 is BLOCKED and returns a nonzero exit code without waiting through the ingestion polling period. Independent signal checks continue so one denial does not hide the others.

To investigate a failed trace check without creating another payment, run the same workflow with `trace_id` set to the recorded trace ID. This mode only reads that stored trace and reports missing services or disconnected/absent DB/outbox evidence. Leave `trace_id` empty for the full acceptance suite. A later successful inspection does not erase the earlier three-minute convergence failure.

Login, wallet readability/funding and payment acceptance are recorded prerequisites. A failed prerequisite writes a complete failure summary. Wallet GETs are polled for transient startup recovery; money-changing POSTs are not automatically retried. The UUID retry key is saved before the payment POST so an interrupted request can be investigated safely. External proxy 504 responses can omit the gateway trace header; those are genuine failed acceptance checks, not evidence of an accepted trace.

## Access requirements and boundaries

Follow [OTEL_ACCESS_SETUP.md](OTEL_ACCESS_SETUP.md) for exact Console steps, role names, an optional dedicated observer identity, and the read-only **OTel access check** workflow. Observability workflows prefer `GCP_OBSERVABILITY_SERVICE_ACCOUNT` when configured and fall back to `GCP_SERVICE_ACCOUNT`. Access is checked before cloud acceptance creates a payment or dashboard publication begins.

The observer needs access to stored data: typically `roles/logging.viewer`, `roles/cloudtrace.user` and `roles/monitoring.viewer`, scoped to this project. Publishing dashboards additionally requires appropriate dashboard create/update permissions, such as `roles/monitoring.dashboardEditor`. Existing service discovery needs Cloud Run read access. Workflows never grant roles, change private-service IAM or expose backend services. If the configured WIF account is denied, use the recorded denial to request the specific access through your administrator.

The Cloud Run Collector still has ephemeral queues. This increment makes no durability guarantee across instance termination, adds no tail-sampling policy, and enables no new browser SDK. Those are separate experiments after this stored-signal baseline is verified.

Official references: [Google OTel/Managed Prometheus mapping](https://docs.cloud.google.com/stackdriver/docs/managed-prometheus/setup-otel), [dashboard API examples](https://docs.cloud.google.com/monitoring/dashboards/api-examples), [Java API with the agent](https://opentelemetry.io/docs/languages/java/api/).
