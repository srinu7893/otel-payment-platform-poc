# Cloud Run production-style review — 13 October 2026

The service-by-service pipeline is prepared for progressive PoC bootstrap. This review performs no cloud deployment, creates no GCP resources and leaves its source changes local. Customer's successful cloud deployment is supplied evidence from the user; the new review changes have not been exercised in Cloud Run.

## Issues found and fixed

1. Canonical URL equality required manually copied URLs and rejected valid display origins. Selected URL is now optional; Cloud Run status.url discovery drives token audiences and health. Available dependency URLs are canonicalized; warnings/actual URLs are saved in discovery.json and success evidence. Legacy release also rerenders routing using discovered origins.
2. Public health checks unnecessarily impersonated a runtime account. Public edges now check anonymously; private services retain authenticated tagged checks, runtime self-Invoker checks and canonical audience. Public disabled-IAM mode is preserved; internal disabled-IAM/public principals are rejected.
3. Missing health-token permissions were discovered only after candidate creation. Selected deployment now verifies actAs via IAM testIamPermissions and verifies private token creation before replacement, naming required roles on failure.
4. Generic CI/config/build/push/digest/deploy steps were repeated eight times. All eight manual/main/WIF workflows now call workflow_service.sh; build_service.sh still packages only the selected application plus needed shared library. No customer-only behavior remains, apart from its intentionally minimal example and focused regression fixture.
5. CloudAMQP virtual-host config was missing. Worker manifests now require a vhost and inject all six Rabbit settings, with TLS 5671 and username/password Secret Manager references. Both worker application YAML files explicitly support SSL/vhost overrides.
6. Native Spring port fallbacks were flattened to 8080. Fallbacks are restored to Auth8079, Customer8084, Bank8082, Gateway8081, Payment8080, Notification8083, API8088. Cloud Run still injects 8080. No business classes changed.
7. A frontend without an API origin could not start Nginx for bootstrap. The cloud image now has a closed-loopback fallback for bootstrap; final online deployment discovers API Gateway. /health is independent; unconfigured API requests remain unavailable. No secret/Collector config reaches frontend.
8. No reusable bootstrap audit/command preparation existed. bootstrap_service.py checks selected live manifests/IAM read-only and generates reviewed commands locally. API-equivalent cpu units/port/probe formatting are accepted. Explicit command sequences cover all seven remaining services in CLOUD_RUN_BOOTSTRAP_COMMANDS.md.
9. Traffic validation lacked clear errors for malformed types. Shared normalizer now rejects invalid revision/latest/percentage types and contradictory API field styles. Existing explicit revision preservation, no-traffic candidate, exact promotion, convergence and rollback remain covered across every service.

## Validation results

- 51 deployment test methods pass, including parameterized contracts for all eight services, all-service success/rollback, partial configuration, discovery warnings, missing bootstrap, private/public checks and bootstrap auditing.
- 13 relevant Maven tests pass: CloudRunIdentity5, CloudRunIdentityFilter2, BusinessStateMetrics1, NotificationSecurity4, DeliveryStateMetrics1. Maven test reactor succeeds. An initial package invocation failed at API Gateway because its JAR is held open by a running local server; the successful test-only invocation avoids repackaging and does not stop those servers.
- Frontend production build passes (Vite, 36 modules).
- All workflows pass actionlint; its optional external ShellCheck integration is disabled because ShellCheck is unavailable. Shared shell syntax is checked separately.
- 36 deployment/application YAML and JSON documents parse. Additional structured checks verify 8 workflow contracts, 8 service-specific Cloud Build contracts and all 7 Java Dockerfile targets/JAR selections.
- Actual Collector YAML validates with otelcol-contrib0.136.0, the same version as its Dockerfile.
- git diff --check passes (only platform line-ending notices).

Docker/Nginx container execution and live GCP IAM/secret/export acceptance were not performed during this review. Collector config validation does not prove exporter delivery. The read-only bootstrap audit must be run in GCP with the appropriate credentials before each first GitHub deployment.

## Remaining manual steps and required roles

Use SERVICE_BY_SERVICE_DEPLOYMENT.md's checklist and CLOUD_RUN_BOOTSTRAP_COMMANDS.md's commands. Confirm existing WIF provider restrictions, repository/APIs, chosen immutable application/Collector digests and the shared runtime account. Populate config.bootstrap.json with real CloudAMQP host/vhost, existing secret references and pinned versions. Grant secretAccessor only on used secrets. Manually create only missing service slots, apply the intended private/public Invoker policy, run bootstrap_service.py check, and manually dispatch only the chosen workflow after exact-commit CI.

github-deployer needs Cloud Run Admin for the PoC, Artifact Registry Writer on the repository, Service Account User and Token Creator on runtime. WIF's constrained subject needs workloadIdentityUser on deployer. Internal services need Invoker for deployer/runtime and callers when accounts are separated. Java runtime observability needs cloudtrace.agent, logging.logWriter and monitoring.metricWriter; no Editor/Owner or runtime Cloud Run Admin. See exact grant commands in the bootstrap document. Existing IAM bindings were not remotely audited in this review.

Recommended application deployment order: Auth -> Customer (already proven) -> Bank -> Gateway -> Payment -> Notification -> API Gateway -> Frontend. Redeploy callers when new dependency URLs become available. Schema/JWT changes require coordinated compatibility checks, not a full-stack deployment.

## Service-specific risks and acceptance

| Service | Remaining acceptance/risk |
|---|---|
| Auth | Correct signing secret/version, login flow, schema migration permission, token compatibility with other JWT consumers |
| Customer | Neon TLS/schema permissions, pool/connection limits, private rejection; existing successful deployment does not validate these new scripts remotely |
| Bank | Fake banking only; schema availability, controlled bank error/timeout scenarios |
| Gateway | Bank discovery and Invoker grant, workload headers and error propagation; readiness may pass with no usable bank route |
| Payment | CloudAMQP TLS/vhost, Neon migration and outbox/reconciliation work; min1/alwaysCPU incurs idle cost; candidate/old revision workers can overlap and produce side effects before traffic promotion |
| Notification | Listener/broker permissions and delivery/duplicate handling, min1 idle cost; old/new consumers may overlap |
| API Gateway | Public edge, JWT enforcement, separate workload/customer headers, complete canonical backend audiences; undeployed/unconfigured routes remain unusable |
| Frontend | API Gateway public HTTPS proxy, Nginx PORT/SNI; bootstrap loopback fallback proves only health and serves no working API |

Shared runtime accounts broaden the effective secret/caller access across services. Revision max1 is not a global singleton. Traffic rollback restores only traffic, not migrations, business writes or messages. Spring readiness alone does not prove actual DB/Rabbit operations. Verify a fake-payment journey, failure/replay behavior and notification, then inspect correlated Cloud Logging, Cloud Trace and Managed Prometheus metrics. Other Java services retain alwaysCPU for async OTel export but min0; frontend is request-billed. Production HA, stricter identity separation, pinned base-image/action supply chain and worker-singleton policy remain future hardening.

## Changed files

- `.github/workflows/deploy-api-gateway.yml`
- `.github/workflows/deploy-auth.yml`
- `.github/workflows/deploy-bank.yml`
- `.github/workflows/deploy-customer.yml`
- `.github/workflows/deploy-frontend.yml`
- `.github/workflows/deploy-gateway.yml`
- `.github/workflows/deploy-notification.yml`
- `.github/workflows/deploy-payment.yml`
- `api-gateway/src/main/resources/application.yml`
- `auth-service/src/main/resources/application.yml`
- `customer-service/src/main/resources/application.yml`
- `deploy/cloud-run/config.bootstrap.example.json`
- `deploy/cloud-run/config.example.json`
- `deploy/cloud-run/config.full-stack.example.json`
- `deploy/cloud-run/service-config.example.json`
- `docs/CLOUD_RUN_BOOTSTRAP_COMMANDS.md`
- `docs/CLOUD_RUN_REVIEW.md`
- `docs/SERVICE_BY_SERVICE_DEPLOYMENT.md`
- `frontend/Dockerfile`
- `gateway-service/src/main/resources/application.yml`
- `mock-bank-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`
- `payment-service/src/main/resources/application.yml`
- `scripts/cloud/bootstrap_service.py`
- `scripts/cloud/deploy_service.py`
- `scripts/cloud/fixtures/full-stack-legacy.json`
- `scripts/cloud/run_release.py`
- `scripts/cloud/test_deploy_service.py`
- `scripts/cloud/test_dynamic_configuration.py`
- `scripts/cloud/test_production_review.py`
- `scripts/cloud/test_run_release.py`
- `scripts/cloud/workflow_service.sh`

## Final stable configuration update

The original review still required per-service runtime mappings. The selected path now accepts `defaultServiceAccount`, with an optional selected override taking precedence, checks live identity before replacement, and rejects missing deployer/runtime Invoker bindings on private services. CloudAMQP `rabbitPort` must be integer5671; TLS cannot be disabled in a final worker manifest. Canonical/display URL pairs and a safe snapshot of live properties are recorded without plaintext environment values. Success evidence records the discovered tag URL and prints the canonical result.

The stable final JSON is `deploy/cloud-run/config.example.json`; no URLs/account mappings need to be edited for each new service. The full-stack/bootstrap examples have the same structure. Remaining manual work is one-time service bootstrap, required Secret Manager versions, IAM grants and real DB/Rabbit/telemetry acceptance. No resources or deployments were executed during this update. Changes remain local.

Final checks rerun on13October: 51 deployment tests, 13 Maven tests, frontend build, all workflow lint, both Bash scripts, 36 YAML/JSON documents, Collector config and git diff --check pass. No deployment/bootstrap commands were executed. Canonical URL mismatch evidence, both private Invoker identities and current live identity/state are covered by tests.
