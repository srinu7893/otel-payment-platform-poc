# Reviewed changes — 2026-10-07

The requested independent Cloud Run deployment implementation includes eight manual-only workflows, eight optional Cloud Build configurations, selected-service build/deploy scripts, service Dockerfile Cloud Run targets, environment-configured Spring settings, and deployment-order documentation. Existing CI is unchanged. The legacy all-service Cloud Run workflow now validates only. No new payment, authentication, transfer, refund or notification processing logic was introduced by the deployment changes.

The complete commit also includes the previously requested native Windows/OTel gap fixes and IDE warning fixes that were already uncommitted in this workspace: native tool setup/launch/logging, Alertmanager/inbox, readiness and synthetic monitoring, service/API operation dashboards, browser performance observations, bounded domain/notification monitoring queries, an index migration, and native integration-test support. These changes were retained under the instruction to commit all changes.

## Review and validation

- Full backend Maven test suite passed in native mode with a separate PostgreSQL test database and RabbitMQ virtual host. XML report totals: tests=73, failures=0, errors=0, skipped=0.
- Eleven cloud deployment contract/rollback tests passed, including selected-service-only rollback and public-access rejection for internal services.
- Three incident-inbox tests passed; frontend production build passed.
- actionlint passed for all eight deployment workflows and the legacy validation workflow. Bash syntax check passed for the selected-service build script.
- YAML selection checks passed for all eight workflows/Cloud Build files and all seven Spring service port/readiness/datasource settings. Native JavaScript and dashboard JSON parse checks passed. Existing CI has no diff.
- Review corrected workflow secret names, exact application JAR selection (avoiding the agent JAR wildcard collision), reserved PORT handling, frontend Cloud Run routing/listening, and gateway health probe configuration.
- Backend builds target only the selected module and required shared library; Collector is reused by digest. Candidate replacement, promotion and rollback are limited to the dedicated selected service.

Docker cannot run on this workstation, so service Docker images were not executed locally. Hosted CI validates container builds. No cloud deployment was run or automatically triggered. WIF/identity permissions, actual service URLs, VPC/subnet, durable storage, secret versions and the existing Collector digest must be configured in gcp-poc before clicking Run workflow. See SERVICE_BY_SERVICE_DEPLOYMENT.md.

The monitoring table scans have timeouts and bounded labels, but representative capacity/HA tests remain future operational work. Domain metrics explicitly depend on the local shared database/schema setup and are disabled by default outside native startup. Historical outage evidence is dated 2026-10-05; latest native monitoring additions need a fresh full-stack rerun after repackaging. The current deployment task does not claim new cloud runtime evidence. Frontend dependency advisories remain outside this deployment change.

## All added/modified files

- `.github/workflows/deploy-api-gateway.yml`
- `.github/workflows/deploy-auth.yml`
- `.github/workflows/deploy-bank.yml`
- `.github/workflows/deploy-customer.yml`
- `.github/workflows/deploy-frontend.yml`
- `.github/workflows/deploy-gateway.yml`
- `.github/workflows/deploy-notification.yml`
- `.github/workflows/deploy-payment.yml`
- `.github/workflows/manual-cloud-run.yml`
- `.gitignore`
- `README.md`
- `api-gateway/Dockerfile`
- `api-gateway/src/main/resources/application.yml`
- `auth-service/Dockerfile`
- `auth-service/src/main/resources/application.yml`
- `auth-service/src/test/java/com/srinu/otelpoc/auth/api/AuthControllerTest.java`
- `cloudbuild-api-gateway.yaml`
- `cloudbuild-auth.yaml`
- `cloudbuild-bank.yaml`
- `cloudbuild-customer.yaml`
- `cloudbuild-frontend.yaml`
- `cloudbuild-gateway.yaml`
- `cloudbuild-notification.yaml`
- `cloudbuild-payment.yaml`
- `customer-service/Dockerfile`
- `customer-service/src/main/java/com/srinu/otelpoc/customer/domain/Customer.java`
- `customer-service/src/main/resources/application.yml`
- `deploy/cloud-run/service-config.example.json`
- `docker-compose.yml`
- `docs/CHANGE_REVIEW.md`
- `docs/LOCAL_GAP_REVIEW.md`
- `docs/NATIVE_WINDOWS_RUNBOOK.md`
- `docs/SERVICE_BY_SERVICE_DEPLOYMENT.md`
- `frontend/Dockerfile`
- `frontend/cloud-run.conf.template`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/main.jsx`
- `frontend/src/telemetry.js`
- `frontend/vite.config.js`
- `gateway-service/Dockerfile`
- `gateway-service/src/main/resources/application.yml`
- `mock-bank-service/Dockerfile`
- `mock-bank-service/src/main/resources/application.yml`
- `mock-bank-service/src/test/java/com/srinu/payments/bank/integration/BankPostgresIntegrationTest.java`
- `notification-service/Dockerfile`
- `notification-service/src/main/java/com/srinu/payments/notification/observability/DeliveryStateMetrics.java`
- `notification-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/db/migration/V3__delivery_window_index.sql`
- `notification-service/src/test/java/com/srinu/payments/notification/api/NotificationSecurityTest.java`
- `notification-service/src/test/java/com/srinu/payments/notification/integration/NotificationRabbitIntegrationTest.java`
- `observability/grafana/dashboards/domain-outcomes.json`
- `observability/grafana/dashboards/platform-overview.json`
- `observability/grafana/dashboards/service-api-gateway.json`
- `observability/grafana/dashboards/service-auth-service.json`
- `observability/grafana/dashboards/service-customer-service.json`
- `observability/grafana/dashboards/service-frontend.json`
- `observability/grafana/dashboards/service-gateway-service.json`
- `observability/grafana/dashboards/service-mock-bank-service.json`
- `observability/grafana/dashboards/service-notification-service.json`
- `observability/grafana/dashboards/service-payment-service.json`
- `observability/incident-inbox/server.py`
- `observability/native-alerts.yaml`
- `payment-service/Dockerfile`
- `payment-service/src/main/java/com/srinu/payments/payment/observability/BusinessStateMetrics.java`
- `payment-service/src/main/java/com/srinu/payments/payment/observability/DomainOutcomeMetrics.java`
- `payment-service/src/main/resources/application.yml`
- `payment-service/src/test/java/com/srinu/payments/payment/observability/DomainOutcomeMetricsTest.java`
- `scripts/build-local.cmd`
- `scripts/check-native.cmd`
- `scripts/cloud/build_service.sh`
- `scripts/cloud/deploy_service.py`
- `scripts/cloud/test_deploy_service.py`
- `scripts/dashboards/generate.py`
- `scripts/discover-local.mjs`
- `scripts/download-native.mjs`
- `scripts/download-support.mjs`
- `scripts/e2e/otel_acceptance.py`
- `scripts/e2e/support-acceptance.py`
- `scripts/e2e/synthetic-journey.py`
- `scripts/env-local.cmd`
- `scripts/local-poc.mjs`
- `scripts/native-assets.mjs`
- `scripts/native-config.mjs`
- `scripts/native-health.mjs`
- `scripts/native-monitor.mjs`
- `scripts/native-poc.mjs`
- `scripts/prepare-ide.cmd`
- `scripts/prepare-native-tests.mjs`
- `scripts/profile-native.mjs`
- `scripts/review-local-dashboards.mjs`
- `scripts/run-poc.cmd`
- `scripts/setup-native.cmd`
- `scripts/tail-local.mjs`
- `scripts/test-backend-native.cmd`
- `scripts/test-native.cmd`
- `scripts/verify-native-additions.mjs`
