# OTel Payment Platform POC

Enterprise-style full-stack payment platform for demonstrating Spring Boot microservices, authentication/authorization, synchronous REST calls, RabbitMQ messaging, PostgreSQL persistence, production-style logging, failure handling and OpenTelemetry traces, metrics and logs.

> All accounts, balances, gateways, email/SMS messages and money movement are simulated. Never use real bank credentials, card data or production secrets in this POC.

## OpenTelemetry implementation and handover

See [the self-contained HTML POC guide](docs/POC_GUIDE.html) for services, architecture, recreation commands, dashboards, source links, support/developer runbooks, success/failure scenarios and execution evidence.

```bash
mvn -B clean verify
bash scripts/setup-otel-agent.sh
bash scripts/otel-compose.sh up --build -d
bash scripts/e2e/wait-ready.sh
```

Grafana: http://localhost:3001 (`admin` / `otel-demo-admin`). The overlay adds Collector, Tempo, Prometheus, Loki and three provisioned dashboards. Backend JVMs export all three signals; the durable outbox now carries W3C trace context into RabbitMQ delivery. The baseline `docker compose` command remains available without OTel.

Verified by the [full manual run](https://github.com/srinu7893/otel-payment-platform-poc/actions/runs/37261777696) on `8ac5ee021a9fc9845dccad2e7444692e89e68e9f`: 63 backend tests with zero failures/errors/skips; all 15 telemetry/security/recovery assertions; payment/refund/replay/risk/P2P scenarios; support health and API contract; 12 concurrent demonstration payments with SENT notifications; real error/slow tail sampling; customer/support browser checks and all three rendered Grafana dashboards. Regenerate the guide after changes with `python3 scripts/render-poc-guide.py`; each manual run also produces a current HTML report alongside its test evidence.

## Current architecture

```text
React Frontend :3000
        |
        v
API Gateway :8088
        |
        +--> Auth Service :8079 ------> PostgreSQL
        +--> Customer Service :8084 --> PostgreSQL
        +--> Payment Service :8080 ---> PostgreSQL
                    |
                    v
             Gateway Service :8081
                    |
                    v
             Mock Bank :8082 ---------> PostgreSQL
                    |
                    +--> account balance / debit / P2P credit
                    +--> immutable bank transaction ledger

Payment / Transfer completion
        |
        v
RabbitMQ :5672
        |
        v
Notification Service :8083 ----------> PostgreSQL
        +--> simulated email
        +--> simulated SMS
        +--> retry/backoff + DLQ
```

RabbitMQ management UI: `http://localhost:15672`

## Baseline security

- Users are stored in PostgreSQL.
- Passwords are BCrypt hashes.
- Auth Service issues a short-lived JWT.
- API Gateway validates the JWT.
- Payment and Notification Services also validate JWTs for defense in depth.
- Roles: `CUSTOMER`, `SUPPORT`, `ADMIN`.
- Customer payment/transfer operations are checked against the authenticated `customer_id`.
- Passwords and JWTs are never written to application logs.

## Demo users

| User | Password | Customer | Role | Linked account |
| --- | --- | --- | --- | --- |
| `demo` | `demo123` | `demo-customer` | CUSTOMER | `ACC1001` |
| `receiver` | `receiver123` | `receiver-customer` | CUSTOMER | `ACC2001` |
| `support` | `support123` | support user | SUPPORT | n/a |
| `admin` | `admin123` | admin user | ADMIN | n/a |

Additional deterministic test accounts include `ACC1002` for insufficient funds, `ACC-SLOW` for latency and `ACC-ERROR` for failure simulation.

## Build and test

```bash
mvn clean test
mvn package -DskipTests
cd frontend
npm install
npm run build
```

GitHub Actions runs backend tests/package, frontend build, Compose validation and container builds on configured pushes/PRs. Full-stack smoke, failure scenarios and OTel E2E run only through the separate `manual-otel-e2e` workflow (`workflow_dispatch`, or manually adding the `run-otel-e2e` label to a same-repository PR). No push or PR-update event triggers E2E.

## Run complete local stack

```bash
docker compose up --build
```

Then open:

```text
http://localhost:3000
```

The frontend talks only to the Edge API Gateway. Internal service-to-service communication uses Docker DNS names such as `gateway-service`, `mock-bank-service` and `customer-service`.

## Login through API Gateway

```bash
curl -s -X POST http://localhost:8088/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: login-001' \
  -d '{"username":"demo","password":"demo123"}'
```

Copy the returned `accessToken` into `TOKEN`:

```bash
TOKEN='<jwt>'
```

## Merchant payment

```bash
curl -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: payment-001' \
  -d '{"idempotencyKey":"pay-001","accountNumber":"ACC1001","merchant":"Demo Store","amount":50.00}'
```

A CUSTOMER cannot submit a different customer's source account.

## P2P transfer

```bash
curl -X POST http://localhost:8088/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: transfer-001' \
  -d '{"idempotencyKey":"transfer-001","senderAccount":"ACC1001","receiverAccount":"ACC2001","amount":125.00,"currency":"INR"}'
```

The mock bank locks both account rows, verifies balance, debits the sender, credits the receiver, stores an immutable bank ledger record and returns the simulated transaction result.

## History and notifications

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/v1/payments
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/v1/transfers
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/v1/notifications
```

CUSTOMER responses are scoped to their own records; SUPPORT/ADMIN operational visibility is being hardened separately.

## Correlation logging

Send or let the gateway generate `X-Correlation-Id`. It is propagated through HTTP calls and RabbitMQ headers and restored into the Notification Service MDC. This business correlation ID complements the active OTel trace/span context exported to Loki. Durable outbox records separately persist W3C parent context to connect scheduled delivery to the original request.

## Delivery plan

Completed locally: baseline business flows; Java agent/Collector; Tempo traces, Loki logs and Prometheus metrics; durable outbox context; operational/business/pipeline dashboards; support UI; manual full-stack recovery/browser acceptance and generated handover.

Next deployment decisions: GCP runtime, identity/secrets/TLS, HA/storage/retention, SLO ownership and alert routing. Cloud Logging/Monitoring and BigQuery remain separate integrations if selected. The guide documents Grafana Cloud/Mimir/Alloy alternatives; none is silently treated as deployed.

Detailed status is maintained in [`docs/BUILD_TRACKER.md`](docs/BUILD_TRACKER.md).
