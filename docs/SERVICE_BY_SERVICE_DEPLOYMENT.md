# Manual service-by-service Cloud Run deployment

The eight `deploy-*.yml` workflows run only through `workflow_dispatch`, only on `main`, after CI passes for that exact commit. A push runs CI, never deployment. All use the `gcp-poc` environment and Workload Identity Federation. Shared `workflow_service.sh` steps check CI, validate configuration, build/push the selected image, resolve its immutable digest, and invoke `deploy_service.py`. No Customer-specific deployment logic remains. Backend builds use `mvn -pl <service> -am`; shared library dependencies are built, other application services are not. Frontend builds only its own Dockerfile. CI remains unchanged.

Project: `project-c9bd3d0e-266f-47bf-852`; region: `us-central1`; repository: `otel-payment-platform`. Deployment identity: `github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com`. PoC runtime identity: `otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com`. Dedicated runtime accounts are also supported.

## GitHub configuration

Environment secrets: `WIF_PROVIDER` (the dedicated OTel provider) and `GCP_SERVICE_ACCOUNT` (github-deployer email). No JSON service-account key is needed. Environment variables: `CLOUD_RUN_CONFIG_JSON`, `COLLECTOR_IMAGE` (existing immutable Collector digest for Java), optional `GRAFANA_URL` for frontend links.

Stable `CLOUD_RUN_CONFIG_JSON` for every service (no URLs or per-service account map required):

```json
{
  "project": "project-c9bd3d0e-266f-47bf-852",
  "region": "us-central1",
  "repository": "otel-payment-platform",
  "defaultServiceAccount": "otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com",
  "databaseSecrets": {
    "url": "neon-jdbc-url",
    "username": "neon-username",
    "password": "neon-password"
  },
  "rabbitHost": "shrimp.rmq.cloudamqp.com",
  "rabbitPort": 5671,
  "rabbitVirtualHost": "dctbblya",
  "rabbitSecrets": {
    "username": "rabbit-username",
    "password": "rabbit-password"
  },
  "jwtSecret": "jwt-secret",
  "secretVersion": "1"
}
```

Account precedence is `serviceAccounts[selected-service]` when explicitly supplied, otherwise `defaultServiceAccount`; missing both fails validation. Optional per-service overrides must be valid identities in the configured project. The live service's `spec.template.spec.serviceAccountName` must match the expected account before any candidate replacement. This guards identity drift rather than silently changing a bootstrapped runtime identity.

`urls` remains optional for backward compatibility and supplied dependency origins. Deployment describes the selected service in the configured project/region and uses live `status.url` for canonical health, token audience, evidence and summary. Valid optional display URL differences produce warnings, and both configured/discovered values appear in evidence. Available outbound dependencies are discovered automatically. Missing dependencies do not block selected deployment; unavailable dependencies retain validated supplied origins. No URLs or account entries need to be added when future services are bootstrapped. Redeploy a caller to discover newly available backends.

Only selected fields are validated: Bank ignores Rabbit/JWT; Gateway ignores datasource/Rabbit/JWT. Workers require `rabbitPort` as integer 5671 and always receive TLS=true, plus host/vhost/secret names. JWT and database references are injected only into their actual consumers. Numeric `secretVersion` applies to required secret references; unrelated fields are ignored. Confirm all secret names/version1 in the stable example actually exist; the configuration contains references, never password values. CloudAMQP host/vhost above are the supplied non-secret values.

`config.full-stack.example.json` and `config.bootstrap.example.json` use this same stable structure. The retired complete-prefix release validator retains an archival test fixture in `scripts/cloud/fixtures/full-stack-legacy.json`; do not use its old full-stack CLI to deploy the dedicated services. All eight active workflows use the shared selected-service path.

Final process: bootstrap a service once, configure its required secrets, apply public/private IAM, then manually run that service's GitHub workflow. It discovers URLs, runtime identity, current traffic/latest-ready revision, ingress/authentication, ports, scaling/billing and existing secret references. `CLOUD_RUN_CONFIG_JSON` stays unchanged between selected service deployments. Missing slots fail with the documented bootstrap diagnostic; runtime mismatch fails before replacement.

## Service contracts

| Service | Cloud Run name | Access | Secrets | Local fallback port | Minimum |
|---|---|---|---|---|---|
| Auth | auth-service | Private | Database + JWT | 8079 | 0 |
| Customer | customer-service | Private | Database | 8084 | 0 |
| Bank | mock-bank-service | Private | Database | 8082 | 0 |
| Gateway adapter | gateway-service | Private | None | 8081 | 0 |
| Payment | payment-service | Private | Database + JWT + Rabbit | 8080 | 1 |
| Notification | notification-service | Private | Database + JWT + Rabbit | 8083 | 1 |
| API Gateway | api-gateway | Public | JWT | 8088 | 0 |
| Frontend | payment-frontend | Public | None | Local Nginx 80 | 0 |

All Cloud Run application containers use port 8080; Cloud Run injects `PORT`, never an explicit reserved env entry. Spring `${PORT:<local-port>}` preserves native fallback ports; Compose retains explicit overrides. Database environment names are `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, supplied only to the five database consumers through pinned Secret Manager references. Neon JDBC must enable TLS. These services own separate Flyway schemas; validate migration permission and shared connection limits before deployment. JWT consumers must use the same sufficiently strong signing secret/version.

RabbitMQ is required only for Payment and Notification. `rabbitHost` must be a hostname; username/password come from `rabbitSecrets` in Secret Manager. `rabbitVirtualHost` is required and should match the assigned CloudAMQP vhost. `rabbitPort` must be the integer 5671. Manifests inject `SPRING_RABBITMQ_HOST`, TLS port `SPRING_RABBITMQ_PORT=5671`, secret-backed `SPRING_RABBITMQ_USERNAME`/`PASSWORD`, `SPRING_RABBITMQ_VIRTUAL_HOST`, and `SPRING_RABBITMQ_SSL_ENABLED=true`. Never place broker passwords in GitHub variables.

All services initially have revision maximum instances 1. Payment's schedulers and Notification's listener require always allocated CPU and minimum instances 1: they cannot scale to zero and incur idle cost. Other Java services retain always allocated CPU for asynchronous Collector/agent export, but minimum 0; they can scale to zero, yet bill while an instance is alive. Frontend uses request-based CPU and minimum 0. Converting Java services to request-based CPU needs a separately tested exporter-flush strategy. Revision maxScale=1 does not guarantee one total process during overlapping old/new revisions. Verify outbox idempotency and Rabbit consumer behavior through rollout; minimum instances on an unpromoted worker candidate can still run background work. No application logic was changed to suppress workers during candidate checks.

## IAM and authenticated verification

Internal services must enforce Invoker IAM and have no `allUsers`/`allAuthenticatedUsers` Invoker grants. Ingress stays `all`; alternate ingress is rejected because external tagged health checks would need a different topology. Private probes generate an ID token using the selected runtime identity, canonical service origin as audience, and the tagged candidate as destination. Customer JWT remains in `Authorization`; workload identity uses `X-Serverless-Authorization`. Deployment preflight checks `iam.serviceAccounts.actAs` and private token creation before replacement, with explicit role diagnostics.

Public edges must either grant `allUsers` Invoker or already disable the Invoker IAM check. The deployment preserves the existing public mode; private deployments always enforce IAM. Public readiness requests are anonymous and require no runtime impersonation. API Gateway's application JWT validation remains enabled. Its workload identity filter obtains metadata-server tokens only for deployment-configured audiences and strips caller-supplied workload headers. Missing dependencies are not usable routes: if none are available, online deployments allow only the selected canonical origin, so IAM initialization succeeds without authorizing unknown backends. Redeploy each caller after new dependencies become available. Offline initial bootstrap manifests may omit workload IAM until origins are discovered; this is bootstrap-only, not the final GitHub deployment.

Required grants, scoped as narrowly as practical:

| Identity | Required roles |
|---|---|
| WIF repository subject | `roles/iam.workloadIdentityUser` on github-deployer, constrained to repository/main/gcp-poc |
| github-deployer | `roles/run.admin` for this PoC, `roles/artifactregistry.writer` on the repository |
| github-deployer on runtime account | `roles/iam.serviceAccountUser`; `roles/iam.serviceAccountTokenCreator` for private health |
| github-deployer and runtime on each internal service | `roles/run.invoker` |
| Runtime on used Secret Manager secrets | `roles/secretmanager.secretAccessor` on each required secret |
| Java runtime project observability | `roles/cloudtrace.agent`, `roles/logging.logWriter`, `roles/monitoring.metricWriter` |

Caller graph: Payment -> Customer/Gateway; Gateway -> Bank; API Gateway -> all six backends. With the shared PoC account, its service-level Invoker grants satisfy these calls. Separate accounts need caller-specific grants. Sharing a runtime identity also shares all its effective secret/invocation permissions; it is not production service isolation. No runtime Editor, Owner or Cloud Run Admin role is required. Existing bootstrap service policy is checked; IAM is never automatically broadened by deployment.

## Traffic and evidence

Both deployment paths normalize API field styles `revisionName`/`revision_name` and `latestRevision`/`latest_revision`. An explicit revision removes the latest flag completely. Latest-only live targets are resolved to the previous `latestReadyRevisionName`; unresolved targets fail locally. Percentages/tags survive and status URLs are removed. Non-boolean latest flags, non-string revisions, invalid percentages and conflicting field styles are rejected.

Before replacement, a local guard verifies the exact prior explicit production split, with zero production traffic for the candidate. Candidate tagging, private/public health, exact revision promotion and traffic convergence follow. Failure after promotion restores that service's saved split. Tag cleanup follows. A client timeout after a successful promotion is covered by rollback tracking set before the API call.

Before/after for illustrative customer revision (not a queried live revision):

```json
{"traffic":[{"revisionName":"customer-service-00012-abc","latestRevision":true,"percent":100}]}
```

```json
{"traffic":[{"percent":100,"revisionName":"customer-service-00012-abc"}]}
```

The example is also in `customer-traffic.example.json`. Uploaded `service-release-<service>-<run-id>` evidence includes `discovery.json` (configured/discovered URLs, warnings, actual runtime identity, live traffic/latest-ready revision, ingress/IAM bindings, ports/resources, scaling/billing and secret references), selected manifest, `previous-traffic.json`, and `result.json` on success. Readiness success proves startup/routing, not business completion or signal delivery. Rollback does not undo migrations, messages or payments already committed. Secret references are visible in artifacts, never plaintext secret values.

## Bootstrap checklist and read-only audit

1. Confirm required APIs, existing repository, WIF provider restrictions and environment variables. Publish the checksum-verified shared Collector image separately and supply its digest.
2. Create/confirm Neon secrets and schema permissions. For Payment/Notification, confirm CloudAMQP TLS/vhost and broker secrets. Confirm JWT secret and numeric versions.
3. Confirm the IAM grants above; keep internal services private. Do not create missing resources through a deployment workflow.
4. Choose an existing immutable application image for each service's initial bootstrap. Generate its manifest/commands using `bootstrap_service.py commands`; this writes local files and prints commands only.
5. Review and manually execute only the intended missing service's commands. Do not replace the proven Customer service unnecessarily. See `CLOUD_RUN_BOOTSTRAP_COMMANDS.md` for all seven remaining service command sequences.
6. Audit the selected slot before its first manual GitHub deployment:

```sh
python3 scripts/cloud/bootstrap_service.py check --service mock-bank-service \
  --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE" \
  --check-deployer-permissions
```

The audit describes only the selected service plus available dependency URLs, checks runtime identity, canonical origin, access policy and Invoker principals, app/Collector port/probes, resource limits, scaling/CPU mode, secret references/versions and exporter configuration. Running with `--check-deployer-permissions` must use github-deployer credentials, not an administrator's identity. It does not test actual secret payloads, broker connectivity, database migrations, project role least privilege, or successful exports; those need live acceptance. Audit JSON is saved locally. It does not modify GCP.

## Recommended deployment order

Manually open Actions -> Deploy <service> -> Run workflow -> main, after exact-commit CI passes:

1. Auth (`deploy-auth.yml`)
2. Customer (`deploy-customer.yml`, already proven)
3. Bank (`deploy-bank.yml`)
4. Gateway (`deploy-gateway.yml`)
5. Payment (`deploy-payment.yml`)
6. Notification (`deploy-notification.yml`)
7. API Gateway (`deploy-api-gateway.yml`)
8. Frontend (`deploy-frontend.yml`)

For later changes, deploy only affected services. Changes to `cloud-runtime` require separately deploying its consumers. Discover dependencies before testing routes. Frontend's gateway URL is runtime Nginx configuration, not required at build time; if missing during bootstrap its proxy points to a closed loopback port and returns an upstream error, while `/health` works. Final GitHub deployment discovers API Gateway and wires HTTPS/SNI, with customer `Authorization` preserved. Frontend has no JDBC, Rabbit, JWT signing or Collector secrets.

## Collector and validation limits

Java Dockerfiles have explicit service JAR paths, cloud targets with the checksum-verified Java agent, and unchanged local targets. `COLLECTOR_IMAGE` must be an immutable digest from the configured registry. Agent OTLP exports to `http://127.0.0.1:4318`. Collector config receives OTLP, applies memory/batch/privacy processors and sends traces/logs through `googlecloud` to Cloud Trace/Logging and metrics through `googlemanagedprometheus`. Health extension listens on 13133; Collector startup probe is finite and app startup depends on it. Invalid Collector configuration fails bounded startup rather than silently disabling exporters. Health readiness does not prove the exporter can reach Google APIs. With no successful export, inspect Collector logs and the writer roles/APIs. Optional Grafana fan-out needs its own pinned token secret. All final Java deployments retain enabled traces/logs/metrics.

Each `cloudbuild-*.yaml` is a manually submitted build/publish alternative and builds only its selected Dockerfile/module. No trigger or deployment is created. Example: `gcloud builds submit . --project=project-c9bd3d0e-266f-47bf-852 --config=cloudbuild-bank.yaml --substitutions=_REVISION=$(git rev-parse HEAD)`. Frontend Cloud Build defaults do not set optional project/Grafana UI link build arguments; use GitHub build for those links. Cloud Build images must still be resolved to digests before bootstrap/deployment.

Source/config validation, deployment unit tests, workflow lint, YAML/JSON parsing, relevant Maven identity/security tests and frontend production build are local evidence. Docker execution is unavailable on this workstation. All remaining cloud/bootstrap commands are generated for manual execution; this review creates no GCP resources and triggers no deployments.

References: [Cloud Run public access](https://docs.cloud.google.com/run/docs/authenticating/public), [service-account impersonation](https://docs.cloud.google.com/docs/authentication/use-service-account-impersonation), [IAM permission preflight](https://docs.cloud.google.com/iam/docs/reference/rest/v1/projects.serviceAccounts/testIamPermissions), [Collector on Cloud Run](https://docs.cloud.google.com/stackdriver/docs/instrumentation/opentelemetry-collector-cloud-run).
