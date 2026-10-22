# Wallet, browser, alerts and collector resilience

For the final demonstration checklist, deployed version-number meanings and minimal Cloud Shell commands, use [Final POC handover](FINAL_POC_HANDOVER.md). The manual **OTel final readiness review** inventories current service versions/readiness and alert activation without cloud mutations.

## What is implemented

Wallet and money-request service methods emit bounded operation spans and Micrometer attempt counters/timers. Operation labels are fixed method names; outcomes are success/rejected/error. Retries count as attempts, not unique money movements. Arguments, exception messages, account/customer IDs and notes are never added to these spans or metric labels.

Wallet timers and browser summaries configure explicit histogram boundaries. The pinned Micrometer bridge otherwise emitted these distributions without boundaries, leaving their cloud panels empty. A real instrumented gateway check verifies the emitted bucket boundaries; dashboard verification still requires finite stored samples.

The bank reports `poc.wallet.deposit.records` and aggregate INR amount from committed `bank.wallet_deposit` rows. The payment service reports `poc.money.request.records` by six stored statuses. Refreshing or replaying a request does not create extra ledger rows or inflate these gauges. These are current database snapshots, not counters: use max across replicas, never sum or rate. Snapshot health and last-success timestamps distinguish cached data from a current reading. Aggregates can differ from user balances, which also include payments, transfers and refunds.

Production browser telemetry uses the same-origin `/api/v1/telemetry/rum` endpoint. Only CLS/FCP/INP/LCP/TTFB/PAGE_VIEW/JS_ERROR and bounded numbers are accepted. It sends no cookies, tokens, URLs, IDs, raw error messages, stacks or DOM. Each page sends at most ten events, including at most three JS errors. Nginx bounds bodies/rate; the gateway independently limits bodies to 512 bytes and 300 requests/minute per instance. The origin allowlist is discovered from the deployed frontend URLs. Origin checks/rate limits are abuse controls, not authentication: reported browser measurements remain untrusted. Lifecycle-dependent measurements are not a complete user census. Web vital times are milliseconds; CLS is a unitless score.

Cloud dashboard generation now produces eleven dashboards: the original eight plus wallet outcomes, browser experience and collector health. A chart is only verified when its historical query has finite samples. Missing data is never replaced with healthy zero values without an observed matching baseline.

## Manual release

Keep the existing service-by-service WIF, exact-source CI gate, immutable digests, candidate health verification and rollback. No deployment runs on push.

1. Wait for CI on the commit being released.
2. Run **Publish OTel collector image**. Download its `collector-image-*` artifact and copy `digest.txt`.
3. Run each selected Java-service deployment with `collector_image` set to that digest. Each workflow builds only its selected application. The default still uses the existing `COLLECTOR_IMAGE` variable when the input is empty. Deploy bank and payment for wallet snapshots, API gateway for browser ingestion, and the other Java services for their collector telemetry. Deploy frontend after the API gateway is ready.
4. Run the existing full cloud acceptance suite and the browser/extended-metric checks. Generate recent wallet/browser traffic, then run **OTel Cloud dashboards** with apply and verification enabled.

## Cold starts and warm mode

Startup CPU boost, liveness probes and bounded read retries remain enabled. A scale-to-zero chain can still take tens of seconds after idle; money-changing POSTs are never blindly retried.

Stored failures showed wallet read timeouts at 25–45 seconds. Cloud settings now allow 45 seconds for customer/bank wallet reads, 90 seconds for the enclosing wallet read, and 180 seconds at the Cloud Run/Nginx boundary. This accommodates a dependency startup chain; it does not make a cold dependency instantly ready. Transfer/debit client timeouts and reconciliation behavior are unchanged.

Before a manual demo, run **Warm OTel demo services**. It calls current private readiness endpoints in parallel using audience-bound identity tokens and records startup duration. It does not deploy, change minimum instances, schedule keepalive traffic, or move money. After a later idle interval the services can scale down again.

To keep the entire login/wallet path warm, merge this optional field into `gcp-poc`'s `CLOUD_RUN_CONFIG_JSON`, then manually redeploy the selected services:

```json
{"warmServices":["api-gateway","auth-service","customer-service","gateway-service","mock-bank-service"]}
```

Payment and notification already retain one instance for background work. `warmServices` is validated and opt-in; this increment does not silently enable five additional always-on services. Set the list back to empty and redeploy to restore scale-to-zero defaults. Warm instances reduce cold starts but cannot guarantee zero starts during replacements. They incur ongoing charges, especially with instance-based CPU billing. [Cloud Run minimum instances](https://docs.cloud.google.com/run/docs/configuring/min-instances).

## Alerts and notification delivery

`scripts/cloud/otel_alerts.py` renders Cloud Monitoring policies for API errors/p95 latency, delivery delays, stale business snapshots and collector queue pressure. API conditions require observed traffic. Per-service telemetry absence policies are disabled by default because idle scale-to-zero is expected; enable them only with a documented always-on expectation and initialized HTTP metrics.

Use **OTel Cloud alerts** with `apply=false` to review. For publication, select an existing enabled/verified notification-channel resource, such as `projects/PROJECT/notificationChannels/123`, and run with `apply=true`. The workflow never creates a channel or grants IAM. The selected WIF account needs `roles/monitoring.alertPolicyEditor` plus channel read access; the previously granted Monitoring Viewer supplies read permissions. An administrator can grant the additional role in Cloud Shell:

```bash
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 \
  --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com \
  --role=roles/monitoring.alertPolicyEditor --condition=None
```

This is an administrator reference, not an action performed by the workflow. [Monitoring roles](https://docs.cloud.google.com/monitoring/access-control).

Local tests evaluate the actual error/latency/no-data/delivery expressions with promtool. A separate isolated real Prometheus -> Alertmanager -> webhook drill verifies firing and resolution delivery. Neither proves delivery to your GCP email/webhook channel. That requires the selected channel, publishing permission, a deliberately isolated cloud test policy, and observed notification/recovery evidence. No real application outage is required for that test.

## Collector durability boundary

The Cloud Run collector exports its internal queue/export/refusal metrics through OTLP to Managed Prometheus. Its 200-request queues are bounded memory queues. The pinned Google exporters own retry behavior and do not accept the generic `retry_on_failure` YAML field; real-binary configuration validation protects against unsupported settings. Internal telemetry shares the exporter path, so a total exporter failure also requires no-data/backend monitoring.

Historical logs also revealed rejected metric writes: duplicate time series in merged batches and samples less than five seconds apart. The metrics path now coalesces cumulative metrics and gauges over ten seconds and exports them through one queue consumer, without the generic batch processor. This avoids merging repeated samples into a single Google write and preserves cumulative totals. Gauge peaks between samples can be lost; this is a sampling tradeoff, not a bank ledger aggregation. The interval processor is alpha in the pinned release and is covered by a real collector regression test. [Pinned processor behavior](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.136.0/processor/intervalprocessor/README.md), [Google exporter restrictions](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.136.0/exporter/googlemanagedprometheusexporter/README.md).

The Google metrics exporter also disables generated `otel_scope_info` and `target_info` metadata. Fresh deployed logs identified duplicate writes to both synthetic gauges, even after application samples were coalesced. Service identity remains in the Prometheus job/instance resource and trace instrumentation remains available; extra scope/resource metadata labels on exported metrics are omitted. The exact pinned collector validates these options before publication.

`scripts/e2e/collector-restart.py` runs the real pinned collector against an isolated failing backend. It enqueues 20 spans, kills the process, restarts using the same `file_storage` directory, and verifies all 20 arrive without resubmission. This demonstrates process-restart recovery on retained disk. Disk-full, corrupt storage, exhausted retries, permanent backend rejection and ingress buffers can still lose data. It is not an exactly-once promise.

Cloud Run instance disk is ephemeral. Adding `file_storage` there does not make queues survive instance replacement. The durable option therefore lives outside Cloud Run:

- `deploy/collector-durable/config.yaml`: TLS + bearer-authenticated gateway with persistent queues for logs/traces/metrics; no asynchronous batch buffer before queue acceptance.
- `deploy/collector-durable/otelcol.service`: systemd service that refuses startup unless `/var/lib/otelcol` is a mounted filesystem.
- `deploy/cloud-run/collector-durable-forward.yaml`: optional sidecar forwarding overlay; direct Google export is replaced, avoiding duplicate fan-out.

Provision or select a private gateway host with a persistent POSIX disk, a trusted TLS certificate, restricted network ingress, and an ADC identity with telemetry writer permissions. Install collector 0.136.0, configure `/etc/otelcol/config.yaml` and a mode-0600 `/etc/otelcol/gateway.env`. That environment supplies `GOOGLE_CLOUD_PROJECT`, `OTEL_TLS_CERT`, `OTEL_TLS_KEY`, `OTEL_INGEST_TOKEN`, and `OTEL_QUEUE_DIRECTORY=/var/lib/otelcol/queues`. The service user needs access only to its queue directory and certificate files. Back up/manage the disk and monitor disk capacity separately; never share one queue directory between collectors.

After the gateway is reachable and its token is stored in Secret Manager, merge:

```json
{"durableCollector":{"endpoint":"https://your-collector-host:4318","tokenSecret":"otel-ingest-token"},"secretVersion":"1"}
```

Deploy the new collector image first, then manually release selected Java services. The renderer uses a secret reference and requires HTTPS without credentials/query/path. Do not paste the token into GitHub config. This option and direct Grafana fan-out are mutually exclusive. A sidecar can still lose unforwarded in-memory data during termination; only data accepted into the external persistent queue has that storage protection. [Collector resiliency](https://opentelemetry.io/docs/collector/resiliency/).

## Local commands (CMD, no Docker)

```cmd
artifacts\native\python\python.exe -m unittest discover -s scripts/cloud -p test_*.py
artifacts\native\python\python.exe scripts\e2e\alert-rules.py --promtool artifacts\native\prometheus\prometheus-3.6.0.windows-amd64\promtool.exe
artifacts\native\python\python.exe scripts\e2e\alert-delivery.py --prometheus artifacts\native\prometheus\prometheus-3.6.0.windows-amd64\prometheus.exe --alertmanager artifacts\native\alertmanager\alertmanager-0.28.1.windows-amd64\alertmanager.exe
artifacts\native\python\python.exe scripts\e2e\collector-restart.py --collector artifacts\native\collector\otelcol-contrib.exe
```

Frontend: run `npm test` and `npm run build` from `frontend`. Java unit tests run locally; Docker-backed persistence integration tests run in existing CI. Generated evidence excludes login tokens and raw telemetry payloads.
