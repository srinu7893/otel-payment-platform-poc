# OTel Payment Platform POC — Phase-by-Phase Delivery Plan

This document is the execution plan for the project. It is intentionally separate from `BUILD_TRACKER.md` so we can track phases, gates, and change flags without losing scope.

## Status flags

- `DONE` — implemented and verified.
- `IN PROGRESS` — currently being built/tested.
- `PLANNED` — accepted backlog item.
- `BLOCKED` — needs manager/external decision.
- `NEEDS UPDATE` — existing feature/code must be changed because architecture or requirements changed.
- `DEFERRED` — intentionally postponed; reason must be documented.

When an existing feature changes, mark it `NEEDS UPDATE`, describe the required change, and return it to `DONE` only after tests/CI pass.

---

## Phase 0 — Architecture & Foundation

| Status | Item | Notes |
| --- | --- | --- |
| DONE | Java 21 + Spring Boot multi-module monorepo | Separate backend services |
| DONE | React + Vite frontend | Customer-facing full-stack UI |
| DONE | Spring Cloud API Gateway | Single browser-facing entry point |
| DONE | Docker Compose | PostgreSQL, RabbitMQ, backend services, frontend |
| DONE | GitHub Actions CI | Backend/frontend/container validation |

## Phase 1 — Core Business Services

| Status | Feature | Acceptance criteria |
| --- | --- | --- |
| DONE | Auth Service | DB users, BCrypt, JWT, CUSTOMER/SUPPORT/ADMIN roles |
| DONE | Customer Service | CRUD, linked demo account, active state |
| DONE | Merchant Payment | Create/get/list/cancel, idempotency, ownership |
| DONE | P2P Transfer | Sender ownership, debit/credit, ledger, idempotency |
| DONE | Mock Bank | Account CRUD, debit, transfer, deterministic failures |
| DONE | Notification Service | RabbitMQ, email/SMS simulation, retry, DLQ |
| PLANNED | Refund/Reversal | Controlled payment and transfer compensation |
| PLANNED | Beneficiary / saved recipient | Optional realistic customer flow |
| PLANNED | Risk/transfer limits | Per-transfer/daily limits and rule-failure scenarios |

## Phase 2 — Security & Authorization

| Status | Feature | Work |
| --- | --- | --- |
| DONE | JWT issuance/validation | Edge + resource-service validation |
| DONE | P2P sender ownership | Source account tied to authenticated customer |
| IN PROGRESS | Consistent service authorization | Customer/Notification/Support/Admin policies standardized |
| PLANNED | Standard JSON 401/403 | Common edge/service error contract |
| PLANNED | Refresh/revocation design | Implement or explicitly document exclusion |
| PLANNED | Rate limiting | Protect login/payment APIs |
| PLANNED | Security audit events | Login failures, authorization failures, admin actions |

## Phase 3 — Database & Data Integrity

| Status | Feature | Work |
| --- | --- | --- |
| IN PROGRESS | Flyway migrations | Separate auth/customer/payment/bank/notification schemas; Hibernate validate-only |
| DONE | Bank transaction ledger | Immutable transaction history |
| DONE | Locking | Concurrency protection around money-changing operations |
| PLANNED | Outbox Pattern | Reliable DB commit + RabbitMQ publication |
| PLANNED | Audit history | Who changed what/when |
| PLANNED | Data retention/archive | DB/log/BigQuery retention documented |

## Phase 4 — Logging, Errors & Resilience

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Correlation ID | HTTP + RabbitMQ propagation and MDC restore |
| IN PROGRESS | Common error model | code/message/path/correlationId/fieldErrors/timestamp |
| PLANNED | Structured JSON logging | service/environment/event/customer/payment/transfer/duration/errorCode |
| PLANNED | HTTP connect/read timeouts | Explicit values on all service clients |
| PLANNED | Circuit breaker | Safe downstream operations only |
| PLANNED | Retry policy | Never blindly retry money movement |
| DONE | Notification retry + DLQ | Retry/backoff and dead-letter topology |

## Phase 5 — Testing & Quality Gates

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Core unit tests | Payment, Bank, Gateway, Customer |
| PLANNED | Auth security tests | JWT/roles/locked-disabled users |
| PLANNED | Authorization/controller tests | 401/403/ownership/support/admin |
| PLANNED | Testcontainers PostgreSQL | Real DB/schema integration tests |
| PLANNED | Testcontainers RabbitMQ | Producer/consumer/retry/DLQ integration |
| IN PROGRESS | Full-stack Docker smoke test | Health → login → payment → P2P → teardown |
| PLANNED | Failure scenario suite | Insufficient funds, slow bank, 500, timeout, duplicate, message failure |

## Phase 6 — Frontend / Support / Admin

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Customer dashboard | Login/payment/P2P/history/notifications |
| DONE | Frontend Docker/Nginx | Production-style static hosting |
| PLANNED | Support dashboard | Search payment/transfer/correlation ID/failures |
| PLANNED | Admin/test dashboard | Trigger slow/error/insufficient scenarios, reset data |
| PLANNED | Service health page | Liveness/readiness overview |
| PLANNED | Observability deep links | Link trace/correlation IDs to Grafana/trace UI later |

## Phase 7 — API & Developer Experience

| Status | Feature | Work |
| --- | --- | --- |
| PLANNED | OpenAPI / Swagger | Contracts, examples, auth, errors, pagination |
| PLANNED | API versioning standards | Keep `/api/v1` conventions explicit |
| PLANNED | Environment profiles | local / docker / gcp |
| PLANNED | Config/secrets strategy | No real secrets committed; Secret Manager later |

## Phase 8 — Baseline Freeze Gate

OpenTelemetry starts only after all of these pass:

- CI green.
- All services boot under Docker Compose.
- Login/payment/P2P/notification flows pass.
- Flyway validated against a clean database.
- Authorization/security tests pass.
- Failure scenarios are reproducible.
- Support/admin demo path is usable.
- Known limitations are documented.

## Phase 9 — OpenTelemetry

| Status | Feature | Work |
| --- | --- | --- |
| PLANNED | OTel Java Agent | Primary zero-code instrumentation |
| PLANNED | OTel Collector | OTLP + resource/memory/batch/filter processors |
| PLANNED | Distributed traces | Gateway → Payment → Gateway Service → Bank → RabbitMQ → Notification |
| PLANNED | Metrics | HTTP/JVM/DB/RabbitMQ/business metrics |
| PLANNED | Trace/log correlation | traceId/spanId in structured logs |
| PLANNED | Jaeger/Tempo + Grafana | Trace UI + metrics dashboards |
| PLANNED | Custom spans | Only meaningful business operations after auto-instrumentation |

## Phase 10 — GCP Deployment & Observability

| Status | Feature | Work |
| --- | --- | --- |
| BLOCKED | GCP runtime | Cloud Run vs GKE vs VM manager decision |
| PLANNED | Artifact Registry | Container image registry |
| PLANNED | Cloud Logging | Central logs |
| PLANNED | Cloud Monitoring | Service/platform metrics |
| BLOCKED | BigQuery logging path | Live path vs analytics/retention decision |
| PLANNED | Grafana | Final dashboards/data sources |
| PLANNED | Secret Manager / Identity | Cloud-managed secrets and workload identity |
| PLANNED | Cost/retention/sampling | Budget and telemetry controls |

---

## Additional enterprise features added to scope

These were not all in the earliest design but strengthen the POC:

- Audit trail for security/admin/state-changing operations.
- Transaction reversal/refund/compensation flow.
- Risk/transfer-limit rules.
- Outbox Pattern for DB + RabbitMQ reliability.
- Structured JSON logs before OTel.
- Standard JSON 401/403 and common error contract.
- API rate limiting at the edge.
- Support and Admin dashboards.
- Service health/readiness UI.
- OpenAPI documentation.
- Local/docker/gcp configuration profiles.
- Data retention policy.
- Observability deep links from support UI.
- Secret/config strategy for GCP.

## Current immediate execution queue

1. Finish Flyway runtime validation and full Docker smoke test.
2. Fix any schema/runtime issues found by CI.
3. Implement common error model + standard 401/403.
4. Add structured JSON logging.
5. Add HTTP timeouts/resilience policies.
6. Build Support/Admin UI.
7. Add Testcontainers + failure tests.
8. Add refund/reversal + risk limits.
9. Add Outbox reliability.
10. Add OpenAPI and satisfy Baseline Freeze gate.
11. Start OpenTelemetry only after the baseline gate is green.
