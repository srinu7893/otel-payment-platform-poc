# Local review after pulling 4d8f7d1

Update on 2026-10-07: this is the historical gap baseline. Subsequent working-tree changes implement native Alertmanager/inbox, endpoint-configurable synthetic checks, scheduled readiness/journey monitoring, API-operation dashboards, trace/error links, domain/delivery snapshots, browser web-vitals and host/database diagnostics. Broker/Collector/persistent-queue drills passed on 2026-10-05. New Cloud Run workflows and review status are described in [SERVICE_BY_SERVICE_DEPLOYMENT.md](SERVICE_BY_SERVICE_DEPLOYMENT.md) and [CHANGE_REVIEW.md](CHANGE_REVIEW.md). Some latest monitoring additions still need a fresh full native-stack rerun after rebuilding; historical runtime evidence does not prove current cloud readiness.

Reviewed 2026-10-05 on native Windows, without Docker. Pulled 14 commits on `feature/otel-observability-manual-e2e`; preserved existing local changes. Services were rebuilt and restarted. This review is local evidence, separate from the upstream Linux/Compose CI evidence.

## Verified

- Maven reactor build succeeded, including new cloud-runtime module and seven Java services. 13 focused tests passed: CloudRunIdentity (5), CloudRunIdentityFilter (2), BusinessStateMetrics (1), DeliveryStateMetrics (1), NotificationSecurity (4). Full unit/integration suite was not executed here.
- All seven application health endpoints passed; frontend, Grafana, Loki, Tempo, Prometheus and Collector were ready. Support operations API reported all dependencies UP; no application ERROR logs were found in the checked three-minute window.
- Fresh simulated payment `9528934c-4dbb-4022-bb0d-12bb21300e7b` completed and its notification reached SENT. Trace `62f2ffee6a29a472654b8f713ed0d597` contains 85 connected spans across six services, including the asynchronous outbox path. Loki returned matching payment logs.
- HTTP/JVM/outbox and new durable payment/notification snapshot metrics are present. Five core scrape targets are UP; Alertmanager is DOWN.
- All 13 dashboards are provisioned. All 136 Prometheus target expressions in the review returned successful query responses. Some panels legitimately have no events or insufficient rate-window samples; query acceptance does not prove all panels have meaningful data. Synthetic metrics are absent. Payment-service dashboard opened in Grafana with no browser errors reported; this was not a visual sweep of every panel/dashboard.

Evidence: `artifacts/latest-build-review.log`, `artifacts/local-smoke.json`, `artifacts/native-health.json`, `artifacts/native-dashboard-review.json`, `artifacts/native-trace-review.json`, `artifacts/latest-payment-dashboard.png`. Always check evidence timestamps after restarting.

## Gaps in priority order

| Priority | Gap and evidence | Next fix / acceptance |
|---|---|---|
| High for local support demo | Native launcher has no Alertmanager/inbox startup. Live Prometheus target `127.0.0.1:9093` refuses connections. `observability/alertmanager.yaml` still expects Compose inbox DNS. | Add native Alertmanager download/config/storage; run Python SQLite inbox bound to loopback with configurable port and database path; map webhook URL. Verify firing → delivery → acknowledgement → recovery → resolution. The documented 9093/9094 links are not usable yet. |
| High for synthetic dashboard | `scripts/e2e/synthetic-journey.py:38` hardcodes localhost:4318; this native Collector uses 14318. No `poc_synthetic_journey_success` series. | Make OTLP endpoint configurable, supply native value, provide a verified Python runtime, run the probe and verify both result JSON and stored metrics. Do not send to the unrelated 4318 listener. |
| High for availability claims | Reporting JVM instances measures metric presence. Probe is on demand; successful request ratio does not establish uptime during no traffic or an outage. | Add scheduled readiness and synthetic checks, missing/stale-data detection, probe timestamps, an explicit availability definition/window and failure/recovery test. |
| Medium for requested function-level dashboards | Per-service dashboards contain route request rates, aggregate p95 and 5xx ratio. They do not yet provide a route/operation table with counts, p50/p95/p99, failures and business outcomes. Health probes contribute traffic. | Add bounded route/operation dimensions, exclude health traffic from business panels, define the requested foundation grouping, and verify panels with success/slow/error traffic. Instrument selected business methods if HTTP routes do not represent the requested functions. |
| Medium for investigation workflow | The Service traces dashboard link goes to generic Explore. The trace/log backends work, but navigation does not automatically select this service or a failing request. | Add prefiltered Tempo links, error-trace queries and trace-to-log drilldown examples; verify a controlled slow/error request. This local rerun exercised the successful path only. |
| Medium for business SLIs | Durable current row counts are not windowed payment success. Transfer/refund counts and full payment-commit-to-notification delivery latency are absent. | Define durable outcomes and timestamps, add bounded metrics, and reconcile against database truth. Consumer received-to-sent mean excludes pre-consumer queue delay and is not p95. |
| Medium for regression confidence | Native broker/Collector outage and persistent queue recovery were not tested after this pull. Full test suite was not run here. | Adapt Docker/Bash acceptance to native lifecycle controls and execute recovery/idempotency tests against an isolated fixture. Keep evidence tied to tested revision. |
| Later, based on need | Browser RUM/web-vitals, host/process saturation, deeper DB diagnostics and profiling are incomplete/absent. Snapshot SQL scans entire PoC tables. | Add measured browser/host signals, query timeouts/indexes or incremental summaries and representative load testing. Do not interpret the frontend dashboard as browser telemetry. |
| Before production/cloud | Local inbox is a demo without production access control or paging. Cloud project/IAM/network/TLS/secrets/endpoints and live cloud acceptance remain outstanding. | Follow `docs/DASHBOARDS_AND_DEPLOYMENT.md`; provision concrete resources and verify ingestion/alert delivery. No cloud deployment was performed. |

Local helper improvements made during review: Java 21 selection scoped to the project (corporate JAVA_HOME pointed at Java 11), executable-name/port checks before skipping tracked processes, explicit System32 paths, and native Alertmanager scrape hostname conversion. Process management still is a development helper, not a Windows service manager; durable process identity and startup supervision are future hardening. Native helper files remain uncommitted.

## Where to look now

Grafana login: `admin` / `otel-demo-admin`.

- [Platform overview](http://localhost:3001/d/platform-overview)
- [Payment service](http://localhost:3001/d/service-payment-service)
- Other service dashboards use `/d/service-api-gateway`, `/d/service-auth-service`, `/d/service-customer-service`, `/d/service-gateway-service`, `/d/service-mock-bank-service`, `/d/service-notification-service`, `/d/service-frontend` on the same Grafana host.
- [Business](http://localhost:3001/d/payment-business), [payment SLO](http://localhost:3001/d/payment-slo), [operations](http://localhost:3001/d/payment-poc), [telemetry pipeline](http://localhost:3001/d/telemetry-pipeline).
- [Raw logs/traces/metrics: Explore](http://localhost:3001/explore). Select Loki, then `{service_name="payment-service"}`. Add `| trace_id="62f2ffee6a29a472654b8f713ed0d597"` for this test.
- Select Tempo in Explore and paste the trace ID. Expand spans to inspect duration, status, events and attributes. The longest spans in this test were API Gateway 1193.7 ms, Payment 1087.1 ms, Gateway adapter 465.7 ms, Bank 218.3 ms, Customer 72.3 ms and asynchronous Notification 235.3 ms. Durations are inclusive and nested; do not add them to calculate total time or attribute all parent time to its own code. These are one test's measurements, not latency baselines.
- For failures, inspect error-status spans, exception events and downstream timing, then correlate logs by trace ID. Inspect business outcome too: a successful HTTP response need not mean a successful payment.
- [Prometheus targets](http://localhost:9090/targets), [alerts](http://localhost:9090/alerts), [RabbitMQ](http://localhost:15672) (`payments` / `payments`).

Edit provisioned dashboards in `observability/grafana/dashboards`; generated service dashboards originate in `scripts/dashboards/generate.py`. Keep changes in source so reprovisioning preserves them.

CMD startup/log commands, prerequisites and every local port are in [NATIVE_WINDOWS_RUNBOOK.md](NATIVE_WINDOWS_RUNBOOK.md). Services are left running for manual testing.
