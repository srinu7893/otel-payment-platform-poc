# Payment Platform POC Build Tracker

This file is the source of truth for what is built, what is being hardened, and what remains before OpenTelemetry and GCP work begin.

## Architecture decisions already made

- Java 21 + Spring Boot multi-module monorepo.
- PostgreSQL for persistent business data.
- RabbitMQ for asynchronous payment/notification events.
- Local service discovery uses Docker DNS and environment-configured service URLs.
- GCP will use the selected platform's native service addressing. Eureka is optional and will only be added as a separate profile if it is an explicit POC requirement.
- Business application is completed and tested before OpenTelemetry is introduced.
- No real bank, real card, real SMS or real money movement is used.

## Services

| Area | Status | Notes |
| --- | --- | --- |
| Auth Service | IN PROGRESS | DB-backed users, BCrypt, JWT, roles/authorities being added |
| Customer Service | BUILT / HARDENING | CRUD, JPA repository, tests, demo customer |
| Payment Service | BUILT / HARDENING | idempotency, status lifecycle, query APIs, cancel, RabbitMQ publisher |
| Gateway Service | BUILT / HARDENING | payment authorization and downstream bank client |
| Mock Bank Service | BUILT / HARDENING | accounts, balance/debit, admin CRUD, failure/latency accounts |
| Notification Service | BUILT / HARDENING | RabbitMQ consumer, persistence, query API; retry/channels next |
| API Gateway | PLANNED | external routing, auth enforcement, correlation propagation |

## Database tables / planned schema

- `user_account` - username, password hash, customer id, enabled/locked, audit timestamps.
- `user_role` - user-to-role mapping (`CUSTOMER`, `SUPPORT`, `ADMIN`).
- `customer` - customer profile, email, mobile, linked account, active state.
- `payment` - immutable identity/idempotency fields plus payment lifecycle/status.
- `bank_account` - simulated account owner, balance, active state, version.
- `bank_transaction` - immutable debit/credit/transfer ledger records. **Next**.
- `notification_record` - notification channel/status/attempt data.
- `outbox_event` - planned production-style reliable event publication table.

## Payment capabilities

- [x] Create payment
- [x] Idempotency key handling
- [x] Get payment
- [x] List/search payment
- [x] Controlled payment cancellation
- [x] RabbitMQ completed-payment event
- [ ] P2P transfer: sender -> receiver
- [ ] Receiver credit and immutable ledger records
- [ ] Transfer history for sender/receiver
- [ ] Simulated refund/reversal

## Authentication / authorization

- [ ] DB-backed users
- [ ] BCrypt password hashing
- [ ] JWT access token issuance
- [ ] JWT validation
- [ ] CUSTOMER role: own profile/payments/transfers/notifications
- [ ] SUPPORT role: operational read-only endpoints
- [ ] ADMIN role: test/admin account operations
- [ ] Unauthorized/forbidden error contracts
- [ ] Security audit logging (success/failure without logging credentials/tokens)

## Production hardening

- [x] Bean Validation in primary APIs
- [x] Transaction boundaries in payment/bank paths
- [x] Optimistic locking in money-related entities
- [x] Structured business-event logging in key paths
- [x] Correlation ID in payment service
- [ ] Correlation ID propagation across every REST call and RabbitMQ event
- [ ] Common error contract across every service
- [ ] Explicit HTTP connect/read timeouts
- [ ] Circuit-breaker policy for safe downstream calls
- [ ] Dead-letter queue for notifications
- [ ] Notification retry/backoff
- [ ] Health/readiness/liveness groups
- [ ] Flyway migrations instead of relying on `ddl-auto`
- [ ] OpenAPI documentation
- [ ] Audit/event history

## Notification channels

- [x] RabbitMQ notification event consumer
- [ ] EMAIL channel adapter (mock/log implementation first)
- [ ] SMS/mobile channel adapter (mock/log implementation first)
- [ ] Channel preference on customer profile
- [ ] FAILED -> RETRYING -> SENT lifecycle
- [ ] DLQ after max retry attempts

## Test strategy

- [x] Payment service unit tests
- [x] Gateway service unit tests
- [x] Bank service unit tests
- [x] Customer service unit tests
- [ ] Auth service unit/security tests
- [ ] Notification consumer tests
- [ ] Controller tests / authorization tests
- [ ] Testcontainers PostgreSQL integration tests
- [ ] Testcontainers RabbitMQ integration tests
- [ ] End-to-end transfer test
- [ ] Failure scenarios: insufficient funds, slow bank, 500, timeout, duplicate request, message failure

## Local platform

- [x] PostgreSQL Docker service
- [x] RabbitMQ Docker service
- [x] Dockerfiles for Spring Boot services
- [x] Docker Compose service addressing
- [x] GitHub Actions Maven CI
- [ ] Baseline CI completely green
- [ ] Docker Compose end-to-end smoke test

## OpenTelemetry phase - do not start until baseline is stable

- [ ] OTel Java Agent primary integration
- [ ] OTLP receiver on OTel Collector
- [ ] Distributed traces
- [ ] JVM/HTTP/DB/RabbitMQ metrics
- [ ] traceId/spanId in structured logs
- [ ] Jaeger or Tempo trace backend
- [ ] Prometheus + Grafana local metrics
- [ ] Collector processors: batch/resource/filter/sampling
- [ ] Optional comparison with Spring Boot OTel starter/manual spans

## GCP phase

- [ ] Select Cloud Run vs GKE vs VM deployment
- [ ] Deploy containers
- [ ] Cloud Logging
- [ ] Cloud Monitoring metrics
- [ ] Logging sink -> BigQuery if manager confirms requirement
- [ ] Grafana datasource/dashboard configuration
- [ ] Secret Manager / workload identity
- [ ] Cost/retention/sampling review

## Manager decisions still required

1. Final GCP runtime: Cloud Run, GKE, or VM?
2. Is BigQuery mandatory in the live log path, or only for analytics/retention?
3. Final trace backend: Jaeger/Tempo POC or company Splunk APM/tracing?
4. Is Eureka specifically required as a demonstration, despite Docker/GCP-native discovery?
5. Should email/SMS remain simulated or should the POC integrate an approved real sandbox provider?

## Next implementation order

1. Make CI green.
2. Finish DB-backed JWT authentication and role authorization.
3. Add P2P transfer + receiver credit + bank transaction ledger.
4. Propagate correlation IDs end-to-end.
5. Notification channel abstraction + retry/DLQ.
6. Testcontainers integration tests and Docker smoke tests.
7. Flyway/OpenAPI/API Gateway hardening.
8. Freeze baseline.
9. Add OpenTelemetry.
10. Deploy to GCP and wire logs/metrics/traces.
