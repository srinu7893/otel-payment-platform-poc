# OTel Payment Platform POC — Phase-by-Phase Delivery Plan

This is the source of truth for baseline completion before OpenTelemetry.

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
| DONE | GitHub Actions CI | Backend tests, frontend build, container build/start, smoke |
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
| NEEDS UPDATE | Service-to-service identity | Customer Service still relies on trusted internal ingress; decide workload identity/client credentials/private ingress for deployment |
| DEFERRED | Refresh/revocation | 30-minute demo token accepted for baseline; production design still required |
| PLANNED | Rate limiting | Edge protection for login and money-moving APIs |
| PLANNED | Security audit table/events | Persist login failures, forbidden access and admin actions |

## Phase 3 — Database & Data Integrity

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Flyway migrations | Service-owned schemas; Hibernate validate-only |
| DONE | Debit/transfer/refund ledgers | Bank-side durable idempotency references |
| DONE | Locking | Deterministic/pessimistic locking around money operations |
| DONE | Transactional Outbox | DB state + event staging atomic; publisher confirms/backoff |
| DONE | Consumer idempotency | Event ID + channel dedupe without breaking retry |
| DONE | Reconciliation state | PROCESSING / RECONCILIATION_REQUIRED with capped scheduled recovery |
| PLANNED | General audit history | Who changed what/when for support/admin actions |
| PLANNED | Data retention/archive | DB/log/BigQuery retention to define before GCP |

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
| PLANNED | Correlation-ID operational search | Persist/index correlation ID or rely on log/trace backend once OTel is present |
| PLANNED | Observability deep links | Trace/log links after Grafana/Tempo/Jaeger exists |

## Phase 7 — API & Developer Experience

| Status | Feature | Work |
| --- | --- | --- |
| PLANNED | OpenAPI / Swagger | Contracts, examples, auth, errors, pagination |
| DONE | `/api/v1` convention | Current public APIs versioned under v1 |
| IN PROGRESS | Environment/config strategy | `.env.example` + Compose externalization done; formal local/docker/gcp profile split still pending |
| DONE | Local secret externalization | DB/Rabbit/JWT values can come from environment; demo defaults remain local-only |
| PLANNED | Cloud secret strategy | GCP Secret Manager/workload identity after runtime choice |

## Phase 8 — Baseline Freeze Gate

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
- NEEDS UPDATE — document/finalize service-to-service identity boundary for deployment.
- PLANNED — OpenAPI/documented endpoint contract.
- PLANNED — final known-limitations/security checklist.

## Phase 9 — OpenTelemetry

| Status | Feature | Work |
| --- | --- | --- |
| PLANNED | OTel Java Agent | Primary zero/low-code instrumentation |
| PLANNED | OTel Collector | OTLP receivers + resource/memory/batch processors |
| PLANNED | Distributed traces | API Gateway → Payment → Gateway → Bank and async Rabbit path |
| PLANNED | Metrics | HTTP/JVM/DB/RabbitMQ/business metrics |
| PLANNED | Trace/log correlation | traceId/spanId injected into structured logs |
| PLANNED | Jaeger/Tempo | Trace backend decision for local/final demo |
| PLANNED | Prometheus + Grafana | Local metric dashboards |
| PLANNED | Custom business spans | Add only after auto-instrumentation is working |

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

1. Add OpenAPI/Swagger and endpoint examples.
2. Add security/audit/known-limitations baseline document.
3. Decide/document service-to-service identity strategy by environment.
4. Mark Baseline Freeze `DONE`.
5. Start OpenTelemetry Java Agent + Collector only after the freeze gate is green.
