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
| DONE | Auth Service | DB users, BCrypt, HS256 JWT, CUSTOMER/SUPPORT/ADMIN roles |
| DONE | Customer Service | CRUD, linked demo account, active state |
| DONE | Merchant Payment | Create/get/list/cancel, idempotency, ownership |
| DONE | P2P Transfer | Sender ownership, debit/credit, ledger, idempotency; end-to-end smoke verified |
| DONE | Mock Bank | Account CRUD, debit, transfer, deterministic failures |
| DONE | Notification Service | RabbitMQ, email/SMS simulation, retry, DLQ |
| PLANNED | Refund/Reversal | Controlled payment and transfer compensation |
| PLANNED | Beneficiary / saved recipient | Optional realistic customer flow |
| PLANNED | Risk/transfer limits | Per-transfer/daily limits and rule-failure scenarios |

## Phase 2 — Security & Authorization

| Status | Feature | Work |
| --- | --- | --- |
| DONE | JWT issuance/validation | Explicit HS256 at issuer and API Gateway; resource validation where currently enabled |
| DONE | P2P sender ownership | Source account tied to authenticated customer |
| NEEDS UPDATE | Consistent service authorization | Customer Service currently relies on edge/internal trust; define service-to-service identity/private-ingress strategy before locking internal APIs |
| IN PROGRESS | Standard JSON 401/403 | Edge Gateway standardized; service-level security handlers/tests still to finish |
| PLANNED | Refresh/revocation design | Implement or explicitly document exclusion |
| PLANNED | Rate limiting | Protect login/payment APIs |
| PLANNED | Security audit events | Login failures, authorization failures, admin actions |

## Phase 3 — Database & Data Integrity

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Flyway migrations | Separate auth/customer/payment/bank/notification schemas; clean Docker database boot verified; Hibernate validate-only |
| DONE | Bank transaction ledger | Immutable transaction history |
| DONE | Locking | Concurrency protection around money-changing operations |
| PLANNED | Outbox Pattern | Reliable DB commit + RabbitMQ publication |
| PLANNED | Audit history | Who changed what/when |
| PLANNED | Data retention/archive | DB/log/BigQuery retention documented |

## Phase 4 — Logging, Errors & Resilience

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Correlation ID | HTTP + RabbitMQ propagation and MDC restore |
| DONE | Common error model | timestamp/code/message/path/correlationId/fieldErrors standardized across application services; edge security errors standardized |
| DONE | Structured JSON logging | Logstash-style JSON console output enabled across services for later Cloud Logging/BigQuery/Grafana ingestion |
| DONE | HTTP connect/read timeouts | Explicit 2s connect / 5s read defaults on payment and gateway synchronous clients |
| PLANNED | Circuit breaker | Safe downstream operations only; no blind replay of money movement |
| PLANNED | Retry policy | Retry only demonstrably idempotent operations; never blindly retry debit/transfer |
| DONE | Notification retry + DLQ | Retry/backoff and dead-letter topology |

## Phase 5 — Testing & Quality Gates

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Core unit tests | Payment, transfer, Bank debit/P2P/idempotency, Gateway, Customer |
| IN PROGRESS | Auth security tests | Added successful login, bad password and locked-user coverage; role/disabled/controller cases remain |
| PLANNED | Authorization/controller tests | 401/403/ownership/support/admin |
| PLANNED | Testcontainers PostgreSQL | Real DB/schema integration tests |
| PLANNED | Testcontainers RabbitMQ | Producer/consumer/retry/DLQ integration |
| DONE | Full-stack Docker smoke test | Health → login → merchant payment → P2P → teardown verified through API Gateway |
| PLANNED | Failure scenario suite | Insufficient funds, slow bank, 500, timeout, duplicate, message failure |
| DONE | CI smoke diagnostics | HTTP status/body plus relevant service logs emitted on smoke failure |

## Phase 6 — Frontend / Support / Admin

| Status | Feature | Work |
| --- | --- | --- |
| DONE | Customer dashboard | Login/payment/P2P/history/notifications |
| DONE | Frontend Docker/Nginx | Production-style static hosting + deterministic container health check |
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
- All services boot under Docker Compose. **Verified.**
- Login/payment/P2P flows pass through the API Gateway. **Verified.**
- Notification flow and retry/DLQ integration test pass.
- Flyway validated against a clean database. **Verified.**
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

## Findings / architecture decisions while building

- The API Gateway must use reactive security (`ReactiveJwtDecoder`) because it is WebFlux; servlet JWT beans prevented gateway startup.
- JWT signing/verification is explicitly HS256 across the current demo flow to avoid runtime algorithm ambiguity.
- Bank P2P initially failed because the gateway and bank transfer paths disagreed; the bank contract is now consistently `/api/v1/bank/**`.
- Customer Service currently relies on edge/internal trust. Do not add superficial JWT protection until service-to-service authentication/private ingress is designed, otherwise Payment → Customer calls break.
- Money-changing calls must not receive generic automatic retries. Use idempotency-aware policies and circuit breakers/timeouts instead.
- CI now prints response bodies and service logs when smoke calls fail; this significantly shortens diagnosis time.
- Docker build contexts are larger than necessary because service/frontend `.dockerignore` files are minimal. Optimize them before the GCP/container-registry phase.
- Mockito reports future-JDK dynamic-agent warnings. Configure the Mockito agent before moving beyond Java 21/current test runtime.
- GitHub Actions reports Node 20 action-runtime deprecation for checkout/setup-node versions currently used; refresh action versions as a CI-maintenance task.

## Current immediate execution queue

1. Finish service authorization/security handlers and controller tests.
2. Add controlled circuit breaker/resilience policies without unsafe money-movement retries.
3. Add Testcontainers PostgreSQL + RabbitMQ and failure scenarios.
4. Build Support/Admin UI + service health page.
5. Add refund/reversal + transfer limits/risk rules.
6. Add Outbox Pattern for reliable event publication.
7. Add OpenAPI, environment profiles and documented secret strategy.
8. Optimize Docker build contexts and CI maintenance warnings.
9. Satisfy the remaining Baseline Freeze gate items.
10. Start OpenTelemetry only after the baseline gate is green.
