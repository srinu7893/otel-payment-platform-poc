## OpenTelemetry acceptance

For the current OTel stack, dashboards, backend assertions and failure/recovery cases, use [POC_GUIDE.html](POC_GUIDE.html). Full-stack E2E is now manual-only via `manual-otel-e2e`; ordinary CI does not start the full stack. The historical baseline procedures below remain useful for individual API checks.

# Full-Stack End-to-End Runbook

This is the baseline runbook for the Spring Boot + React payment platform before OpenTelemetry is added.

## 1. Prerequisites

- Docker Desktop / Docker Engine with Compose
- Ports available: `3000`, `5432`, `5672`, `15672`, `8079`, `8080`, `8081`, `8082`, `8083`, `8084`, `8088`

## 2. Optional local environment file

```bash
cp .env.example .env
```

The repository defaults are demo-only. For anything outside local POC usage, change DB/Rabbit/JWT values.

## 3. Start everything

```bash
docker compose up --build -d
```

Check status:

```bash
docker compose ps
```

Check all backend health endpoints:

```bash
for p in 8079 8084 8082 8081 8080 8083 8088; do
  curl -fsS http://localhost:$p/actuator/health && echo " <- $p"
done
```

## 4. Open the frontend

- UI: `http://localhost:3000`
- RabbitMQ management: `http://localhost:15672`

Demo users:

| Username | Password | Role |
| --- | --- | --- |
| `demo` | `demo123` | CUSTOMER |
| `receiver` | `receiver123` | CUSTOMER |
| `support` | `support123` | SUPPORT |
| `admin` | `admin123` | ADMIN |

Demo accounts:

- `ACC1001` — primary customer sender/account
- `ACC2001` — receiver account
- `ACC1002` — low-balance account used by bank-side tests

## 5. Customer acceptance flow

Login as `demo` and verify:

1. Customer overview loads.
2. Merchant payment succeeds for a small amount.
3. Payment appears in payment history.
4. `PAYMENT_COMPLETED` notification appears asynchronously.
5. P2P transfer from `ACC1001` to `ACC2001` succeeds.
6. Transfer appears in transfer history.
7. Transfer notification appears.
8. Refund a completed merchant payment.
9. Refund becomes `COMPLETED`.
10. `PAYMENT_REFUNDED` notification appears.
11. A payment above configured risk maximum returns a `RISK_LIMIT_EXCEEDED` error.
12. A bank-side insufficient-funds case returns a business failure rather than a server error.

## 6. Support/Admin acceptance flow

Login as `support` or `admin` and verify:

1. No customer-profile lookup is required.
2. Operations dashboard loads.
3. Service health shows Auth, Customer, Payment, Gateway, Mock Bank and Notification.
4. Recent payments/transfers/notifications are visible according to privileged backend access.
5. A known Payment or Transfer UUID can be looked up directly.
6. Failed / reconciliation-required records are highlighted.

Correlation-ID search is intentionally not marked complete yet because correlation IDs are not yet persisted/indexed as business-operation fields. OTel trace/log search will later complement this.

## 7. Important reliability behavior

Money-changing operations are not blindly retried.

- Payment and transfer calls use idempotent bank transaction IDs.
- Unknown downstream outcomes become `RECONCILIATION_REQUIRED`.
- Scheduled reconcilers reuse the same operation ID.
- Refunds are idempotent by refund ID.
- Outbox events use at-least-once delivery.
- Notification consumers deduplicate by event ID/channel.
- Poison RabbitMQ messages are retried then dead-lettered.

## 8. Troubleshooting

Show application logs:

```bash
docker compose logs --tail=200 api-gateway payment-service gateway-service mock-bank-service notification-service
```

Follow logs live:

```bash
docker compose logs -f payment-service gateway-service mock-bank-service notification-service
```

Reset everything to a clean database/queues:

```bash
docker compose down -v
docker compose up --build -d
```

## 9. Stop platform

```bash
docker compose down
```

Delete local DB/Rabbit volumes too:

```bash
docker compose down -v
```

## 10. Baseline freeze definition

Before OpenTelemetry starts, baseline acceptance requires:

- Maven/test suite green.
- React production build green.
- Docker Compose validation/build/start green.
- All service health checks green.
- Customer payment/P2P/refund paths green.
- RabbitMQ outbox/notification path green.
- Risk and failure cases reproducible.
- Support/Admin operational view usable.
- Known service-to-service identity limitation documented.

Once these are green, freeze business behavior and add OTel as a separate observability phase.
