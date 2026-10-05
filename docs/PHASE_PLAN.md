# OTel Payment Platform POC — Phase-by-Phase Delivery Plan

This records baseline and local OpenTelemetry completion, followed by separately scoped cloud deployment work.

## Status flags

- `DONE` — implemented and verified by tests/CI.
- `IN PROGRESS` — implemented or partially implemented, final verification/remaining edge cases pending.
- `PLANNED` — accepted backlog item.
- `BLOCKED` — needs manager/external decision.
- `NEEDS UPDATE` — existing implementation must change before baseline freeze.
- `DEFERRED` — intentionally postponed with reason.

---

## Phase 0 — Architecture & Foundation

| Status | Item | Notes |
| --- | --- | --- |
| DONE | Java 21 + Spring Boot multi-module monorepo | Separate backend services |
| DONE | React + Vite frontend | Customer + role-aware support/admin UI |
| DONE | Spring Cloud API Gateway | Single browser-facing entry point |
| DONE | Docker Compose | PostgreSQL, RabbitMQ, services and frontend |
| DONE | GitHub Actions CI | Backend tests, frontend/container builds; full-stack acceptance is opt-in |
| DONE | E2E runbook | `docs/E2E_RUNBOOK.md` |

## Phase 1 — Core Business Services

| Status | Feature | Acceptance criteria |
| --- | --- | --- |
| DONE | Auth Service | DB users, BCrypt, HS256 JWT, CUSTOMER/SUPPORT/ADMIN |
| DONE | Customer Service | CRUD, linked demo account, active state |
| DONE | Merchant Payment | Create/get/list/cancel, ownership, idempotency, reconciliation |
| DONE | P2P Transfer | Debit/credit, locks, ledger, idempotency, reconciliation |
| DONE | Mock Bank | Account CRUD, debit/transfer/refund ledgers, deterministic failures |
| DONE | Notification Service | RabbitMQ, email/SMS simulation, retries, DLQ, dedupe |
| DONE | Refund / compensation | Full merchant-payment refund, bank credit idempotency, reconciliation, notification |
| DONE | Risk controls | Per-transaction + daily customer/account exposure checks |
| DEFERRED | Beneficiary / saved recipient | Nice-to-have product feature; not required for OTel baseline |

## Phase 2 — Security & Authorization

| Status | Feature | Work |
| --- | --- | --- |
| DONE | JWT issuance/validation | Explicit HS256 at issuer/gateway/resource services where enabled |
| DONE | Customer payment/P2P ownership | Source account tied to authenticated customer |
| DONE | Standard JSON 401/403 | Edge + secured services use structured security errors |
| DONE | Role-aware frontend | CUSTOMER and SUPPORT/ADMIN no longer share customer-profile assumptions |
| DONE | Operations route authorization | `/api/v1/ops/**` restricted to SUPPORT/ADMIN |
| DONE | Service-to-service identity strategy | Local Docker trust boundary documented; GCP workload/private-ingress strategy defined in `docs/SERVICE_IDENTITY_STRATEGY.md` |
| DEFERRED | Refresh/revocation | 30-minute demo token accepted for baseline; production design still required |
| DEFERRED | Rate limiting | Production/API-management hardening; intentionally not a blocker for local OTel baseline |
| DEFERRED | Persistent security audit table/events | Useful production hardening; structured security logs exist now and OTel/log backend will be added next |

## Phase 3 — Database & Data Integrity

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Flyway migrations | Service-owned schemas; Hibernate validate-only |
| DONE | Debit/transfer/refund ledgers | Bank-side durable idempotency references |
| DONE | Locking | Deterministic/pessimistic locking around money operations |
| DONE | Transactional Outbox | DB state + event staging atomic; publisher confirms/backoff |
| DONE | Consumer idempotency | Event ID + channel dedupe without breaking retry |
| DONE | Reconciliation state | PROCESSING / RECONCILIATION_REQUIRED with capped scheduled recovery |
| DEFERRED | General audit history | Post-OTel/admin hardening; not required for baseline tracing/metrics/logging |
| DEFERRED | Data retention/archive | Define with GCP logging/BigQuery retention policy |

## Phase 4 — Logging, Errors & Resilience

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Correlation ID | HTTP + RabbitMQ propagation and MDC restore |
| DONE | Common error model | timestamp/code/message/path/correlationId/fieldErrors |
| DONE | Structured JSON logging | Logstash-style console JSON across services |
| DONE | Sensitive log masking | Account identifiers masked in operational logs |
| DONE | HTTP timeouts | Explicit synchronous connect/read timeouts |
| DONE | Circuit breaker | Gateway→Bank protected; transport ambiguity maps to UNKNOWN |
| DONE | Safe retry model | No blind retry of money movement; operation IDs make reconciliation replay safe |
| DONE | Notification retry + DLQ | Retry/backoff + poison-event dead-letter integration coverage |
| DONE | Failure scenario coverage | Insufficient funds, risk rejection, duplicate/idempotent replay, poison-message DLQ, slow-bank timeout and simulated bank-500 acceptance verified |

## Phase 5 — Testing & Quality Gates

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Core unit tests | Payment, transfer, refund, risk, Bank, Gateway, Customer, Auth |
| DONE | Security/controller tests | Standard 401/403 and missing-resource regression cases |
| DONE | Testcontainers PostgreSQL | Flyway, locks, debit replay, transfer and refund idempotency |
| DONE | Testcontainers RabbitMQ | Real producer/consumer, dedupe and poison-message DLQ |
| DONE | Full-stack Docker smoke | Login → payment → outbox notification → refund → risk → P2P |
| DONE | CI diagnostics | Response/status/log dump on smoke failures |
| DONE | Support/Admin direct smoke | SUPPORT login + `/api/v1/ops/health` verified in dedicated pre-OTel acceptance workflow |

## Phase 6 — Frontend / Support / Admin

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Customer dashboard | Login, payment, P2P, histories, notifications |
| DONE | Refund UI | Completed payments can initiate refund from customer UI |
| DONE | Support/Admin dashboard | Privileged list/search/problem summary without customer-profile dependency |
| DONE | Service health backend | API Gateway aggregates six service actuator health checks |
| DONE | Service health UI acceptance | Operations health backend + focused acceptance verified; role-aware UI builds successfully |
| PLANNED | Failure-scenario admin controls | Controlled slow/error/reset actions for live OTel demonstrations |
| DONE | Correlation-ID operational search | Support UI links to Loki correlation/trace lookup; database correlation indexing remains separate |
| DONE | Observability deep links | Support UI and Grafana trace/log links provisioned; live Grafana backend queries and browser dashboards verified |

## Phase 7 — API & Developer Experience

| Status | Feature | Work |
| --- | --- | --- |
| DONE | OpenAPI / Swagger | Gateway serves `/openapi.yaml` + Swagger UI; pre-OTel acceptance checks runtime reachability |
| DONE | `/api/v1` convention | Current public APIs versioned under v1 |
| DONE | Environment/config baseline | `.env.example` + Compose externalization complete; cloud-specific identity/secrets remain deployment concerns |
| DONE | Local secret externalization | DB/Rabbit/JWT values can come from environment; demo defaults remain local-only |
| PLANNED | Cloud secret strategy | GCP Secret Manager/workload identity after runtime choice |

## Phase 8 — Baseline Freeze Gate

**BASELINE FREEZE: DONE** — pre-OTel application baseline verified by the consolidated CI pipeline.

OpenTelemetry starts only after these are satisfied:

- DONE — CI green.
- DONE — all services boot under Docker Compose.
- DONE — login/payment/P2P/refund/risk flow passes through API Gateway.
- DONE — Outbox → RabbitMQ → notification path passes.
- DONE — retry/DLQ integration passes.
- DONE — Flyway clean database startup passes.
- DONE — Testcontainers PostgreSQL/RabbitMQ pass.
- DONE — customer frontend production build passes.
- DONE — Support/Admin frontend production build passes.
- DONE — focused Support/Admin operations-health acceptance call.
- DONE — explicit slow-bank timeout + simulated HTTP 500 scenario in pre-OTel acceptance.
- DONE — service-to-service identity boundary documented for local Docker and GCP deployment.
- DONE — OpenAPI documented endpoint contract + Swagger UI.
- DONE — security baseline and known limitations documented.

## Phase 9 implementation update

Verified by the [full manual run](https://github.com/srinu7893/otel-payment-platform-poc/actions/runs/37261777696) on `8ac5ee021a9fc9845dccad2e7444692e89e68e9f`: 63 backend tests with zero failures/errors/skips; all 15 telemetry/security/recovery assertions; payment/refund/replay/risk/P2P scenarios; support health and API contract; 12 concurrent demonstration payments with SENT notifications; real error/slow tail sampling; customer/support browser checks and all three rendered Grafana dashboards.

See `POC_GUIDE.html` for precise implemented coverage, reproduction, evidence and exclusions.

## Phase 9 — OpenTelemetry

| Status | Feature | Verified work |
| --- | --- | --- |
| DONE | OTel Java Agent | Seven backend JVMs; checksum-verified pinned agent |
| DONE | OTel Collector | OTLP, resource/privacy/memory/batch processors; live signal pipelines |
| DONE | Distributed traces | Connected HTTP/JDBC/outbox/Rabbit consumer trace across six services |
| DONE | Metrics | Seven service HTTP series, JVM, outbox attempt counter and service graphs; Collector ingress/export queues and RabbitMQ depth/consumers/DLQ |
| DONE | Trace/log correlation | Exact trace ID found in Loki; Grafana datasource proxy retrieves real Tempo spans |
| DONE | Tempo | Selected trace backend with service-graph/span metrics generator |
| DONE | Prometheus + Grafana | Three dashboards / 26 panels, rendered with real queries |
| DONE | Custom span and durable context | outbox.publish, persisted W3C traceparent/tracestate, attempt metric |
| DONE | Recovery acceptance | Broker outage catch-up; application availability and fresh traces after Collector recovery; persisted trace/log queues recover after backend outage and Collector SIGKILL |
| DONE | Advanced sampling | Actual error and seven-second slow traces retained; normal traffic reduced; full-trace mode restored |
| DONE | Current handover | Regeneratable HTML, observed results, screenshots and manual-only workflow |

## Phase 10 — GCP Deployment & Observability

| Status | Feature | Work |
| --- | --- | --- |
| BLOCKED | GCP runtime | Cloud Run vs GKE vs VM manager decision |
| PLANNED | Artifact Registry | Container images |
| PLANNED | Cloud Logging | Central JSON logs |
| PLANNED | Cloud Monitoring | Platform/service metrics |
| BLOCKED | BigQuery logging path | Manager decision: mandatory path vs analytics/retention |
| PLANNED | Grafana | Final dashboards/data sources |
| PLANNED | Secret Manager / workload identity | Cloud-managed secrets/service identity |
| PLANNED | Cost/retention/sampling | Budget + telemetry volume controls |

---

## Known findings that must not be lost

- Gateway is WebFlux and must use reactive Spring Security components.
- Money-changing operations must never use generic blind HTTP retries.
- Bank-side operation IDs make merchant debit, transfer and refund replay-safe.
- `UNKNOWN` infrastructure outcomes are not business `FAILED`; they enter reconciliation.
- Outbox delivery is at-least-once, therefore consumers must be idempotent.
- Customer Service internal authorization is still an explicit deployment design decision, not something to hide with superficial JWT wiring.
- Correlation IDs are propagated/logged but are not yet persisted/indexed for database operational search.
- Demo credentials/default secrets are local POC values only.

## Current immediate execution queue

1. Review the verified observability PR and its manual-run evidence.
2. Recreate the manager demo using `POC_GUIDE.html` / `scripts/run-poc.sh`.
3. Choose the GCP runtime and production identity, ingress, retention and alert routing before Phase 10.


## Cloud preparation and advanced support increment

Implemented: Cloud Run PORT support and agent-baked containers; private caller ID tokens alongside customer JWT; support probe authentication; per-service runtime manifests with Secret Manager references, VPC/TLS broker settings and Collector managed exports; manual WIF/digest/readiness/traffic/rollback release; separate opt-in cloud business/signal acceptance; linked BigQuery SQL/runbook; fourth technical SLO dashboard, burn rules and deterministic tests; Collector body redaction and synthetic stored-log acceptance.

Status is evidence-specific: local builds and manual integration checks are recorded in the current guide. Live cloud execution requires this repo's GCP environment settings, existing service slots, durable DB/broker, identities and secrets. Infrastructure bootstrap, actual BigQuery link/schema validation, cloud/Grafana datasource mapping, paging, rolling worker safety, load/HA/DR and complete privacy/RUM/profiling remain pending. See [CLOUD_RUN_PLAN.md](CLOUD_RUN_PLAN.md).
