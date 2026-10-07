# Native Windows OTel POC — no Docker

The application and telemetry stack run as Windows processes. The local scripts adapt observability configuration to Windows paths and loopback addresses. Native startup now includes Alertmanager, the incident inbox and scheduled readiness/journey monitoring. See [change review](CHANGE_REVIEW.md) for current verification and [service deployments](SERVICE_BY_SERVICE_DEPLOYMENT.md) for Cloud Run.

## Access links

| Purpose | URL | Demo login |
| --- | --- | --- |
| Customer app | http://localhost:3000 | `demo` / `demo123` |
| Support operations | http://localhost:3000 | `support` / `support123` |
| Admin operations | http://localhost:3000 | `admin` / `admin123` |
| Grafana | http://localhost:3001 | `admin` / `otel-demo-admin` |
| Operations dashboard | http://localhost:3001/d/payment-poc | Grafana login |
| Platform overview | http://localhost:3001/d/platform-overview | Grafana login |
| Payment service dashboard | http://localhost:3001/d/service-payment-service | Grafana login |
| Payment SLO dashboard | http://localhost:3001/d/payment-slo | Grafana login |
| Business dashboard | http://localhost:3001/d/payment-business | Grafana login |
| Collector / RabbitMQ dashboard | http://localhost:3001/d/telemetry-pipeline | Grafana login |
| Logs / traces / metrics explorer | http://localhost:3001/explore | Grafana login |
| Grafana data sources | http://localhost:3001/connections/datasources | Grafana login |
| Prometheus | http://localhost:9090 | none |
| Prometheus targets | http://localhost:9090/targets | none |
| Prometheus alerts | http://localhost:9090/alerts | none |
| Alertmanager | http://localhost:9093 | none; local demo |
| Incident inbox | http://localhost:9094 | none; local demo |
| Native readiness/browser/database metrics | http://localhost:9097/metrics | none; loopback |
| Domain outcomes and delivery dashboard | http://localhost:3001/d/domain-outcomes | Grafana login |
| RabbitMQ | http://localhost:15672 | `payments` / `payments` |
| Swagger | http://localhost:8088/swagger-ui.html | none |
| OpenAPI | http://localhost:8088/openapi.yaml | none |
| API health | http://localhost:8088/actuator/health | none |
| Collector health | http://localhost:13133 | none |
| Collector internal metrics | http://localhost:8888/metrics | none |
| Application metrics exported by Collector | http://localhost:8889/metrics | none |
| RabbitMQ metrics | http://localhost:15692/metrics/per-object | none |
| Tempo readiness (API, not UI) | http://localhost:3200/ready | none |
| Loki readiness (API, not UI) | http://localhost:3100/ready | none |

## Start, check, logs, stop

Open CMD:

```bat
cd /d C:\Users\padegapati.reddy\POC\otel-payment-platform-poc
call scripts\env-local.cmd
scripts\run-poc.cmd start
scripts\run-poc.cmd status
scripts\run-poc.cmd check
```

`start` launches background processes. `status` checks listening ports; `check` waits for application health and verifies a simulated payment and its telemetry. Rerunning `start` skips tracked live processes. Services remain available until stopped or Windows restarts.

```bat
scripts\run-poc.cmd logs payment-service
scripts\run-poc.cmd logs otel-collector
node scripts\tail-local.mjs artifacts\native-runtime\logs\rabbitmq.log
scripts\run-poc.cmd stop
```

Ctrl+C stops log following only. Log names include `api-gateway`, `auth-service`, `customer-service`, `gateway-service`, `mock-bank-service`, `notification-service`, `frontend`, `grafana`, `tempo`, `loki`, and `prometheus`. `stop` terminates recorded project processes and shuts down the separate PostgreSQL instance; it preserves data. Stop manual test traffic first. The existing IT PostgreSQL service on 5432 is unaffected.

After pulling new code, stop this project, rebuild, and start again:

```bat
git pull --ff-only
scripts\run-poc.cmd stop
scripts\run-poc.cmd build
scripts\run-poc.cmd start
```

If frontend dependencies changed, run `npm ci` in `frontend`. `env-local.cmd` configures paths for the current CMD; no global PATH changes are required.

If the Java editor reports `Missing artifact com.srinu.otelpoc:cloud-runtime`, run `scripts\prepare-ide.cmd`. This installs the root POM and shared library in the normal Maven repository used by the editor; the native build's separate `artifacts/maven-cache` does not populate that repository. Repeat after changing the shared library or parent POM. In VS Code, run **Java: Reload Projects** afterward if old diagnostics remain. If needed, run **Java: Clean Java Language Server Workspace** and allow the reload. These editor messages can remain cached even after Maven resolves the dependency.

## Manual test and telemetry navigation

1. Sign in as `demo`, open **Payment**, and pay a small simulated amount from ACC1001. Check **History** and **Notifications**. Try a transfer to ACC2001 or refund a completed payment.
2. In Grafana, set **Last 15 minutes** and refresh after 15–30 seconds. The dashboards show service/JVM activity, business events, and Collector/RabbitMQ health.
3. In **Explore → Loki**, run `{service_name="payment-service"}`. For one trace: `{service_name="payment-service"} | trace_id="PASTE_TRACE_ID"`.
4. In **Explore → Tempo**, paste a trace ID or run TraceQL `{ resource.service.name = "payment-service" }`. Expand API Gateway → Payment → Customer / Gateway → Bank, then outbox → RabbitMQ → Notification.
5. Grafana's log/trace links correlate a request. Support/Admin also has an Observability panel with trace-ID and correlation-ID lookup.
6. In Prometheus, try `http_server_request_duration_seconds_count`, `jvm_memory_used_bytes`, `poc_outbox_publish_attempts_total`, `otelcol_exporter_queue_size`, `rabbitmq_queue_messages_ready`, and `traces_service_graph_request_total`. All seven jobs, including Alertmanager and native-monitor, should be UP after complete startup.

`check` creates a simulated payment of 1 and verifies its notification, connected spans, matching logs, scrape targets, metrics, dashboards, and data-source connections. Successful evidence goes to `artifacts/local-smoke.json` with IDs and timestamp. A failed rerun does not replace prior success evidence; check the timestamp and exit code.

## Tools and native configuration

IT-installed tools: Java 21.0.1 at `C:\Program Files\Zulu\zulu-21`, Maven 3.9.16 at `C:\Program Files\apache-maven-3.9.16`, PostgreSQL 17 at `C:\Program Files\PostgreSQL\17`. Node 24.3.0 is installed.

Downloaded versions: Java agent 2.20.0, Collector contrib 0.136.0, Tempo 2.9.0, Loki 3.5.5, Prometheus 3.6.0, Grafana OSS 12.2.0, RabbitMQ 4.1.4, Erlang OTP 27.3.4.3. Archives and agent are SHA256-verified.

Tools are in `artifacts/native`; data, configs, logs, and PIDs are in `artifacts/native-runtime`. `scripts\setup-native.cmd` recreates the downloads/build/dependencies when needed. It requires internet and the IT-installed Java/Maven/PostgreSQL paths. Allow several GB of disk for tools, dependencies, and telemetry, plus memory for seven JVMs and the other processes.

Port changes avoid unrelated processes:

- PostgreSQL: `127.0.0.1:55432`; database/user/password `payments`. Separate project cluster, not the existing 5432 database.
- Internal Gateway: `18081`; the existing 8081 process remains untouched.
- Collector OTLP: gRPC `14317`, HTTP `14318`; existing 4317/4318 listeners remain untouched.
- Tempo OTLP: `24317/24318`; query API stays `3200`.
- Loki internal gRPC: `9096`; Tempo internal gRPC: `9095`.
- API Gateway `8088`; Auth `8079`; Customer `8084`; Payment `8080`; Bank `8082`; Notification `8083`.

PostgreSQL 17 and RabbitMQ 4.1 differ from the original Compose images (PostgreSQL 16 and RabbitMQ 3). Local smoke checks passed; this is not identical to the container acceptance environment.

## Source files

- `scripts/env-local.cmd`: tool paths.
- `scripts/native-poc.mjs`: processes, ports, service environment, logs, stop.
- `scripts/native-config.mjs`: local configuration generation.
- `observability/collector.yaml`: three signal pipelines and persistent export queues.
- `observability/prometheus.yaml`, `observability/alerts.yaml`: scrape jobs and alerts.
- `observability/grafana/provisioning/datasources/datasources.yaml`: data sources and correlation links.
- `observability/grafana/dashboards/`: fourteen dashboards; edit generated service dashboards through `scripts/dashboards/generate.py` to preserve reproducibility.
- `scripts/e2e/otel_acceptance.py`: deeper security/failure/resilience checks, with Docker/Bash assumptions for outage tests.
- `docs/POC_GUIDE.html`: earlier generated guide; use this runbook for current Windows commands.

## Verified and remaining

Verified again on 2026-10-05 after pulling `4d8f7d1`: all Java modules built; 13 focused tests passed; automated payment notification delivered; six services and durable outbox shared an 85-span connected trace; exact trace-correlated logs in Loki; HTTP/JVM/durable business metrics in Prometheus; five core scrape targets healthy; all thirteen dashboards provisioned and their Prometheus queries accepted. Payment service dashboard opened in the browser. Evidence and limitations are in [the gap review](LOCAL_GAP_REVIEW.md).

The native support lifecycle and broker/Collector/persistent-export-queue outage tests passed on 2026-10-05. Native integration tests use a separate `poc_integration` database and `poc-integration` RabbitMQ virtual host. Run `scripts\test-backend-native.cmd` for all backend tests; run `scripts\test-native.cmd support` or `scripts\test-native.cmd resilience` for live drills (these interrupt project dependencies and restore them). Run `node scripts\profile-native.mjs payment-service` for a 30-second JFR recording. Scheduled monitoring creates one simulated payment every five minutes while running; disable with `POC_SYNTHETIC_ENABLED=false` before starting native-monitor. Browser observations are local-development only. Remaining operational work includes startup supervision after reboot, representative capacity/HA tests, and authenticated cloud deployment/acceptance. Frontend dependency advisories remain recorded separately. No cloud account is required for local testing.

Official references: [RabbitMQ Windows](https://www.rabbitmq.com/docs/install-windows-manual), [Erlang compatibility](https://www.rabbitmq.com/docs/which-erlang), [Grafana download](https://grafana.com/grafana/download/12.2.0?edition=oss), [Tempo release](https://github.com/grafana/tempo/releases/tag/v2.9.0), [Collector release](https://github.com/open-telemetry/opentelemetry-collector-releases/releases/tag/v0.136.0).
