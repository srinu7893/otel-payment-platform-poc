# OTel Payment Platform POC

Enterprise-style Spring Boot microservices platform for demonstrating business flow, production-style logging, failure handling, distributed tracing, metrics, and GCP observability.

## Current services
- auth-service : 8079
- payment-service : 8080
- gateway-service : 8081
- mock-bank-service : 8082
- notification-service : 8083
- customer-service : 8084
- PostgreSQL : 5432
- RabbitMQ : 5672 / management UI 15672

## Baseline business flow

```text
Login -> Customer -> Payment -> Gateway -> Mock Bank -> DB
                                      |
                                      -> RabbitMQ -> Notification
```

All bank accounts and funds are simulated. Never use real bank credentials, card data, or production secrets.

## Phase 1
Build and validate the complete business application without OpenTelemetry.

## Phase 2
Add OpenTelemetry Java Agent + Collector, traces, metrics, and correlated logs.

## Phase 3
Deploy agreed components to GCP and connect Cloud Logging / Monitoring / BigQuery / Grafana.

## Local test credentials
- username: demo
- password: demo123
- customer id: demo-customer
- linked demo account: ACC1001

## Build
```bash
mvn clean package
```

## Run
```bash
docker compose up --build
```

## Login
```bash
curl -X POST http://localhost:8079/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo123"}'
```

## Create payment
```bash
curl -X POST http://localhost:8080/api/v1/payments -H 'Content-Type: application/json' -H 'X-Correlation-Id: demo-001' -d '{"idempotencyKey":"pay-001","accountNumber":"ACC1001","merchant":"Demo Store","amount":50.00}'
```
