# Pre-OpenTelemetry Security Baseline

This document defines the security boundary accepted for the local/Docker POC before OpenTelemetry is added. It also records the deployment decisions that must change for GCP/production-like environments.

## Implemented now

- BCrypt password hashing in Auth Service.
- HS256 JWT issuance with a 30-minute demo lifetime.
- JWT validation at API Gateway and secured resource services where enabled.
- CUSTOMER / SUPPORT / ADMIN roles.
- Customer ownership validation for merchant payment and P2P sender account.
- SUPPORT/ADMIN-only `/api/v1/ops/**` route.
- Structured 401/403 JSON responses.
- Correlation IDs on HTTP/RabbitMQ paths.
- Account identifiers masked in logs.
- Environment-variable configuration for DB/Rabbit/JWT/risk settings.
- No real credentials or production secrets are required by the repository.
- Bank debit/transfer/refund operations are idempotent by operation ID.
- No blind retry of money-moving requests.
- UNKNOWN transport outcomes enter reconciliation instead of being declared business failures.

## Local/Docker trust boundary

The current local POC intentionally uses one Docker network. Customer Service is consumed internally by Payment Service without a separate service credential. This is acceptable for the local observability lab because the services are not intended to be Internet-exposed individually.

The browser must use the API Gateway. Mock Bank, Gateway Service, RabbitMQ, PostgreSQL and internal service ports are implementation details and should not be exposed in a production deployment.

## Deployment identity strategy

The exact implementation depends on the selected GCP runtime, so this is documented rather than faked in the local baseline.

Preferred order:

1. **Cloud Run** — private service ingress plus authenticated service-to-service invocation using Google service identities/ID tokens; only the edge service is public.
2. **GKE** — private ClusterIP services, Workload Identity, NetworkPolicy and optionally mTLS/service mesh if required by the environment.
3. **VM** — private VPC addressing, firewall rules and an explicit client-credentials/mTLS mechanism for service identity.

Whichever runtime is chosen, Payment Service → Customer Service and other internal calls must authenticate as a workload/service identity rather than reusing end-user passwords.

## Deliberately deferred from the local OTel baseline

- Refresh-token rotation / token revocation store.
- Persistent security audit table.
- Edge rate limiting.
- Real email/SMS providers.
- Production certificate/mTLS management.
- Secret Manager wiring.
- WAF / DDoS policy.
- Fine-grained admin entitlements beyond the current role model.

These are not hidden gaps; they are deployment/production controls that are not necessary to demonstrate distributed observability safely in the local POC.

## Security rules that must remain true

- Never log passwords, JWT values, database passwords or full financial account identifiers.
- Never expose Mock Bank administration APIs publicly in a deployed environment.
- Never automatically retry an ambiguous money-moving POST unless replay safety is guaranteed by the same operation ID.
- Preserve idempotency keys and bank operation IDs during reconciliation.
- Treat `RECONCILIATION_REQUIRED` as an operational/manual-review state after the automatic attempt cap is reached.
- Keep demo credentials and local fallback secrets out of production configuration.
- Store cloud secrets in the platform secret store rather than source control.

## Known limitations before OTel

- JWT signing uses one symmetric HS256 demo secret; production-like deployments should prefer centrally managed asymmetric signing or an external identity provider where appropriate.
- Customer Service service-to-service identity is not enforced in the local Docker topology.
- Correlation IDs are searchable in logs but not persisted as a first-class database index for every aggregate.
- Support/Admin operational search is intentionally basic until the log/trace backend exists.
- Demo email/SMS adapters do not send real messages.
- Saved beneficiaries and partial refunds are outside the baseline scope.

## Baseline acceptance

The baseline is ready for OpenTelemetry when:

- GitHub CI is green.
- Docker Compose starts all services.
- Customer payment/P2P/refund/risk flows pass.
- Transactional outbox and Rabbit notification delivery pass.
- PostgreSQL and RabbitMQ Testcontainers pass.
- SUPPORT operations health reports all required services UP.
- Slow-bank timeout and bank-500 scenarios result in `RECONCILIATION_REQUIRED` rather than duplicate money movement or an incorrect terminal failure.
- The OpenAPI contract and this security baseline are checked in.
