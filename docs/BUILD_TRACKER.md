# Payment Platform POC Build Tracker

This file is the source of truth for what is built, what is being hardened, and what remains before OpenTelemetry and GCP work begin. Update this file as features are committed so the project scope is never lost.

## Architecture decisions already made

- Java 21 + Spring Boot multi-module monorepo.
- React + Vite frontend.
- Spring Cloud Gateway is the browser-facing edge/API gateway.
- PostgreSQL for persistent business data.
- RabbitMQ for asynchronous payment/notification events.
- Each database-owning microservice now owns a dedicated PostgreSQL schema (`auth`, `customer`, `payment`, `bank`, `notification`) with Flyway migrations.
- Local service discovery uses Docker DNS and environment-configured service URLs.
- GCP will use the selected platform's native service addressing. Eureka is optional and will only be added as a separate profile if it is an explicit POC requirement.
- Business application is completed and tested before OpenTelemetry is introduced.
- No real bank, real card, real SMS or real money movement is used.

## Services

| Area | Status | Notes |
| --- | --- | --- |
| Frontend | BUILT / HARDENING | React login, dashboard, payment, P2P transfer, history, notifications, nginx container |
| API Gateway | BUILT / HARDENING | routing, JWT validation, correlation ID, authenticated context headers |
| Auth Service | BUILT / HARDENING | DB-backed users, BCrypt, JWT issuance, CUSTOMER/SUPPORT/ADMIN roles, Flyway auth schema |
| Customer Service | BUILT / HARDENING | CRUD, JPA repository, seeded sender/receiver customers, Flyway customer schema |
| Payment Service | BUILT / HARDENING | merchant payments, P2P transfers, idempotency, ownership validation, JWT resource server, Flyway payment schema |
| Gateway Service | BUILT / HARDENING | merchant authorization + P2P transfer authorization to mock bank |
| Mock Bank Service | BUILT / HARDENING | account CRUD, debit, P2P debit/credit, row locking, ledger, failure/latency accounts, Flyway bank schema |
| Notification Service | BUILT / HARDENING | RabbitMQ payment/transfer consumer, email/SMS simulation, retry/backoff, DLQ, Flyway notification schema |

## Database tables / planned schema

- [x] `auth.user_account` - username, BCrypt password hash, customer id, enabled/locked, audit timestamps.
- [x] `auth.user_role` - user-to-role mapping (`CUSTOMER`, `SUPPORT`, `ADMIN`).
- [x] `customer.customers` - customer profile, email, linked demo account, active state.
- [x] `payment.payments` - payment id, idempotency, customer/account, merchant, amount, state and timestamps.
- [x] `payment.transfers` - P2P transfer identity, customer, sender/receiver, amount, state, bank transaction reference.
- [x] `bank.bank_account` - simulated account owner, balance, active state, optimistic version.
- [x] `bank.bank_transaction` - immutable debit/credit/transfer ledger records.
- [x] `notification.notifications` - event, channel, delivery lifecycle, attempts, failure information.
- [ ] `payment.outbox_event` - production-style reliable event publication table.
- [x] Flyway V1 migrations created for all database-owning services.
- [x] Hibernate schema mutation replaced with `ddl-auto=validate` in database-owning services.
- [ ] Full runtime Flyway/Hibernate validation must pass the new Docker smoke test before this item is considered baseline-frozen.

## Merchant payment capabilities

- [x] Create payment.
- [x] Idempotency key handling.
- [x] Get payment.
- [x] Paginated list/search by status.
- [x] Controlled payment cancellation.
- [x] Mask account data in API response/logging.
- [x] RabbitMQ completed-payment event.
- [x] Merchant payment ownership associated with authenticated `customer_id`.
- [x] CUSTOMER payment history/get/cancel restricted to owned records; SUPPORT/ADMIN retain operational access.
- [ ] Simulated refund/reversal.

## P2P transfer capabilities

- [x] Authenticated sender -> receiver transfer API.
- [x] Verify sender account belongs to authenticated JWT `customer_id`.
- [x] Receiver account seeded for full-stack demo (`ACC2001`).
- [x] Transactional sender debit + receiver credit.
- [x] Deterministic row locking to reduce account deadlock risk.
- [x] Bank-side idempotency by transfer/payment id.
- [x] Immutable bank transaction ledger.
- [x] Customer-scoped transfer history; SUPPORT/ADMIN can inspect all.
- [x] RabbitMQ transfer-completed event.
- [ ] Refund/reversal/compensation flow.
- [ ] Explicit transfer limits/risk rules for richer demo scenarios.

## Authentication / authorization

- [x] DB-backed users.
- [x] BCrypt password hashing.
- [x] JWT access-token issuance.
- [x] Edge gateway JWT validation.
- [x] Payment Service JWT validation (defense in depth).
- [x] Notification Service JWT validation.
- [x] `CUSTOMER`, `SUPPORT`, `ADMIN` roles in JWT.
- [x] P2P sender ownership enforcement.
- [x] Merchant payment ownership enforcement.
- [x] CUSTOMER notification history is customer-scoped.
- [x] Security audit logging for successful/failed login without logging passwords/tokens.
- [ ] Customer Service endpoint authorization completed consistently.
- [ ] SUPPORT role restricted to explicit operational read-only endpoints.
- [ ] ADMIN role restricted to explicit admin/test scenario operations.
- [ ] Standard JSON 401/403 contracts at gateway and resource services.
- [ ] Refresh-token/session-revocation design (optional for POC; document if not implemented).

## API Gateway / full-stack UI

- [x] Single edge gateway on port 8088.
- [x] Routes Auth, Customer, Payment, Transfer and Notification APIs.
- [x] Gateway generates/preserves `X-Correlation-Id`.
- [x] Gateway derives authenticated user/customer/roles from JWT and overwrites spoofable context headers.
- [x] React login through gateway.
- [x] Customer overview screen.
- [x] Merchant payment screen.
- [x] P2P send-money screen.
- [x] Payment/transfer history views.
- [x] Notification view.
- [x] Frontend production nginx container and Docker Compose service.
- [ ] Support operations dashboard.
- [ ] Admin/test-scenario dashboard.
- [ ] Service-health page for demo/support use.

## Production hardening

- [x] Bean Validation in primary APIs.
- [x] Transaction boundaries in payment/bank paths.
- [x] Optimistic locking in money-related entities.
- [x] Structured business-event logging in key paths.
- [x] Correlation ID HTTP filters in edge/auth/customer/payment/gateway/bank/notification services.
- [x] Correlation propagation Payment -> Gateway -> Bank and Payment -> Customer.
- [x] Correlation ID included in RabbitMQ message headers.
- [x] Rabbit consumer restores correlation ID into MDC.
- [x] Standard payment/transfer validation and core exception handling.
- [x] Flyway migrations introduced with service-owned PostgreSQL schemas.
- [ ] Common error contract across every service.
- [ ] Structured JSON Logback configuration for every service.
- [ ] Explicit HTTP connect/read timeouts.
- [ ] Circuit-breaker policy for safe downstream calls.
- [x] Dead-letter queue topology for notifications.
- [x] Notification retry/backoff configuration.
- [x] Health/readiness probes enabled on key services.
- [ ] Health/readiness/liveness groups standardized across all services.
- [ ] OpenAPI documentation.
- [ ] Outbox pattern for database + RabbitMQ delivery reliability.
- [ ] Audit/event history.
- [ ] Rate limiting at edge.

## Notification channels

- [x] RabbitMQ payment event consumer.
- [x] RabbitMQ transfer event consumer.
- [x] EMAIL channel abstraction with simulated sender.
- [x] SMS/mobile channel abstraction with simulated sender.
- [x] RECEIVED -> PROCESSING/RETRYING -> SENT/FAILED lifecycle model.
- [x] Retry/backoff configuration.
- [x] DLQ after rejected/exhausted message processing.
- [ ] Customer preference (`EMAIL`, `SMS`, both) stored in customer profile.
- [ ] Obtain real customer email/mobile from Customer Service rather than placeholder destination values.
- [ ] Explicit retry/redrive administration API for failed notifications.

## Demo accounts and deterministic scenarios

- `demo / demo123` -> `demo-customer` -> sender `ACC1001`.
- `receiver / receiver123` -> `receiver-customer` -> receiver `ACC2001`.
- `support / support123` -> SUPPORT role.
- `admin / admin123` -> ADMIN role.
- `ACC1002` -> insufficient-funds scenario.
- `ACC-SLOW` -> deliberate latency scenario.
- `ACC-ERROR` -> downstream failure scenario.

## Test strategy

- [x] Payment service unit tests.
- [x] Gateway service unit tests.
- [x] Bank service unit tests.
- [x] Customer service unit tests.
- [x] Transfer application-service ownership/idempotency/success tests.
- [ ] Auth service unit/security tests.
- [ ] Bank transfer tests (balance mutation, ledger, idempotency, insufficient funds).
- [ ] Notification sender/delivery/consumer tests.
- [ ] Controller/JWT authorization tests.
- [ ] API Gateway route/security tests.
- [ ] Testcontainers PostgreSQL integration tests.
- [ ] Testcontainers RabbitMQ integration tests.
- [ ] Dedicated end-to-end test suite.
- [x] CI full-stack smoke workflow now performs login + merchant payment + P2P transfer after booting Docker Compose.
- [ ] Failure scenarios in CI: insufficient funds, slow bank, 500, timeout, duplicate request, message failure/DLQ.

## Local platform / CI

- [x] PostgreSQL Docker service.
- [x] RabbitMQ Docker service + management UI.
- [x] Dockerfiles for backend Spring Boot services including API Gateway.
- [x] Docker Compose service addressing.
- [x] GitHub Actions Java 21 backend build/test/package.
- [x] GitHub Actions Node frontend install/build.
- [x] Frontend container-image build validation.
- [x] Full-stack Docker Compose build/start/health/login/payment/transfer smoke test added to CI.
- [ ] Latest Flyway/full-stack smoke run must complete green; fix runtime issues before moving to the next hardening block.

## OpenTelemetry phase - do not start until baseline is stable

- [ ] OTel Java Agent primary integration.
- [ ] OTLP receiver on OTel Collector.
- [ ] Distributed traces through Gateway -> Payment -> Gateway Service -> Bank and RabbitMQ -> Notification.
- [ ] JVM/HTTP/DB/RabbitMQ metrics.
- [ ] `traceId`/`spanId` injected into structured logs.
- [ ] Jaeger or Tempo trace backend.
- [ ] Prometheus + Grafana local metrics.
- [ ] Collector processors: resource, memory limiter, batch, filter and sampling.
- [ ] Compare zero-code Java Agent vs Spring Boot OTel Starter/library instrumentation.
- [ ] Add a few meaningful custom spans only after auto-instrumentation (for example `payment.authorize`, `bank.balance.check`).

## GCP phase

- [ ] Select Cloud Run vs GKE vs VM deployment.
- [ ] Container registry / Artifact Registry.
- [ ] Deploy containers.
- [ ] Cloud SQL/PostgreSQL decision for deployed persistence.
- [ ] RabbitMQ deployment/replacement decision for GCP POC.
- [ ] Cloud Logging.
- [ ] Cloud Monitoring metrics.
- [ ] Logging sink -> BigQuery if manager confirms requirement.
- [ ] Grafana datasource/dashboard configuration.
- [ ] Secret Manager / workload identity.
- [ ] Cost/retention/sampling review.

## Manager decisions still required

1. Final GCP runtime: Cloud Run, GKE, or VM?
2. Is BigQuery mandatory in the live log path, or only for analytics/retention?
3. Final trace backend: Jaeger/Tempo POC or company Splunk APM/tracing?
4. Is Eureka specifically required as a demonstration, despite Docker/GCP-native discovery?
5. Should email/SMS remain simulated or should the POC integrate an approved real sandbox provider?

These decisions do not block the local enterprise baseline; work should continue while they are pending.

## Current implementation order

1. Finish Flyway/runtime Docker smoke validation and fix any schema/startup issue.
2. Add common JSON error contract and structured JSON logging.
3. Add explicit REST connect/read timeouts and safe resilience rules.
4. Add Support/Admin frontend views and service-health page.
5. Add Testcontainers PostgreSQL/RabbitMQ integration tests.
6. Add refund/reversal scenarios and notification preference/customer-contact integration.
7. Add Outbox pattern and OpenAPI documentation.
8. Freeze/test baseline application.
9. Add OpenTelemetry Java Agent + Collector and all three signals.
10. Deploy to GCP and wire Cloud Logging/Monitoring/BigQuery/Grafana as agreed.
