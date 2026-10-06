# Production hardening and OpenTelemetry expansion

Reviewed 2026-10-06 against PR #3 head 4d8f7d1dc342861e6f9fd9016da1c930b6be7901.
This document adds an implementation backlog, not implementation evidence.
All new items below are PLANNED. Existing recorded local acceptance remains scoped to its tested revision.
Cloud Run is the user's selected deployment target; live cloud validation remains pending.

## Delivery order and completion rules

Implement hardening first, then browser/business telemetry, then advanced experiments.
Every increment must link source SHA, meaningful tests, exact manual acceptance run, observed output and known limitations.
An experiment is not a production guarantee. Do not mark external integrations complete with mocks.

## 1. Hardening gates

| Priority | Work | Acceptance |
|---|---|---|
| P0 | Telemetry data minimization at application source and Collector | Synthetic JWT/password/email/account markers absent from stdout and stored traces/logs/metrics; nested attributes, exceptions, URLs and baggage covered; business IDs retained only where needed |
| P0 | Secure telemetry ingress and support surfaces | Unauthorized OTLP and incident operations rejected; allowed origins/payload sizes/request rates bounded; secrets absent from frontend bundle |
| P0 | Cross-revision outbox/reconciliation/consumer safety | Two workers plus terminate/restart exercise: no duplicate bank mutation, no lost durable event, notifications deduplicate; verify lock/claim behavior and bounded recovery |
| P0 | Image and dependency security | Exact-image scanning and SBOM attached to release; immutable digests; exception policy with owner/expiry; least-privilege container settings verified |
| P1 | Login/API abuse controls and session strategy | Meaningful 429/expiry/role tests, documented limiter scope across instances, refresh/revocation or selected IdP design |
| P1 | Durable audit trail | Actor/action/outcome/time recorded for admin changes and redrive; immutable access policy; credentials excluded |
| P1 | Load and instrumentation overhead | Fixed workload with and without instrumentation; record p50/p95/p99, throughput, errors, CPU/RAM, DB pools and queue backlog; thresholds stated before run |
| P1 | Backup/recovery and retention | Restore persisted fake bank ledger/business/outbox state into clean environment; prove reconciliation and duplicate-safe notification; measured RPO/RTO |
| External gate | SSO and real incident routing | Select IdP/pager and owners; verify live login/access restrictions, alert dedupe, acknowledgement and recovery delivery; no credentials required for research |
| Cloud gate | IAM/TLS/secrets/rollout and cloud signals | Real private/public auth negatives, verified DB/broker TLS, exact revision business flow and stored cloud trace/log/metric evidence |

## 2. Useful OpenTelemetry additions

| Feature | Purpose | Acceptance |
|---|---|---|
| Browser fetch/document/user-action traces | Connect customer action to backend payment journey | Same-origin ingestion proxy; allowed propagation targets; browser and backend trace relationship retrieved from backend; feature flag and privacy-safe attributes |
| Web vitals and JS errors | Distinguish frontend slowness from bank/API delay | Explicit separate measurements for LCP/INP/CLS and sanitized errors; browser instrumentation alone must not be labelled complete RUM |
| Business completion/delivery SLIs | Detect failed or stuck business outcomes despite HTTP 200 | Durable state/counters reconciled with SQL; replay/refund/reconciliation semantics defined; stale snapshots cannot count as healthy; approved targets remain external |
| Exemplars | Navigate latency histogram to a real trace | Confirm actual exemplar emitted, retained trace retrievable and Grafana click-through works |
| Domain spans/events and async links | Explain risk/refund/reconciliation decisions and delayed work | Assert domain attributes, statuses, links and parentage across retry/fan-out; HTTP success distinguished from declined/unknown outcome |
| Safe baggage | Carry bounded non-sensitive diagnostic context | Allowlist, size limits and untrusted-boundary stripping; no customer/payment IDs as unbounded metric labels |
| Cardinality/resource conventions | Keep queries stable and cost bounded | Route templates rather than raw URLs; service/version/environment/instance labels verified; series count bounded under unique-ID workload |
| Database/broker infrastructure telemetry | Separate pool pressure from DB/broker failure | Secured receiver/exporter credentials; real pool/connection/lock/backlog scenarios; no public management endpoints |
| Collector resilience/scale | Observe loss, backpressure and sampling limits | Queue overflow, prolonged outage and restart tests; document expected loss; trace-aware routing for multi-Collector tail sampling |
| Agent versus Spring Boot starter | Understand instrumentation choices | Separate profiles with equivalent scenario coverage and overhead measurements; prevent duplicate SDKs/spans/metrics |

## 3. Advanced evaluation, not default production dependencies

- Profiling: evaluate JVM/JFR or chosen profiling backend and trace correlation; distinguish backend feature support from OTel profiles maturity.
- eBPF/host telemetry: separate Linux/VM/GKE lab. Do not assume host-level access on managed Cloud Run.
- OpAMP: evaluate Collector fleet configuration/version management only after a concrete fleet requirement and trust model exist.
- Multi-backend export: test routing, backend failure isolation, cost and privacy policies separately.
- Synthetic journeys and fault injection: extend existing manual tests; keep fake money and deliberate outages in isolated environments.
- Incident effectiveness: compare timed diagnosis of identical incidents using logs-only versus correlated telemetry; report measured MTTR, not assumed improvement.

## OpenTelemetry versus surrounding tooling

OpenTelemetry supplies APIs/SDKs, instrumentation, OTLP and Collector processing/export.
Tempo/Loki/Prometheus store/query signals; Grafana visualizes them.
SLO rules, paging, SSO, backups, business correctness and HA are application/platform responsibilities.
Research integrations for Splunk and AppDynamics against exact deployed versions and approved endpoints before changing exporters.

## Official reference map

- Signals and maturity: https://opentelemetry.io/docs/concepts/signals/
- JavaScript/browser status: https://opentelemetry.io/docs/languages/js/
- Data minimization and Collector redaction: https://opentelemetry.io/docs/security/handling-sensitive-data/
- Collector queues, persistence and data-loss cases: https://opentelemetry.io/docs/collector/resiliency/
- Java instrumentation ecosystem: https://opentelemetry.io/docs/languages/java/
- Sampling: https://opentelemetry.io/docs/concepts/sampling/
- Semantic conventions: https://opentelemetry.io/docs/concepts/semantic-conventions/

## Current execution constraint

At planning time the execution environment is unavailable. No application changes or new test runs are claimed by this document.
Next runnable increment: audit raw stdout and exported signal privacy, then implement source-level controls and meaningful stored-signal regression tests.
