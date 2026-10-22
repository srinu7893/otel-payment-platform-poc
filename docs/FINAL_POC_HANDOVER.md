# Final payment / OpenTelemetry POC handover

## Ready for the demonstration

The latest full business/API verification on 21 October 2026 passed 99 collection requests and 230 assertions. Deployed Swagger passed 16 browser checks; mobile/error recovery passed five. Eleven Cloud Monitoring dashboards previously had finite samples for all 84 panel queries. These are dated observations, not a guarantee of current ingestion: rerun the checks below before presenting.

Open [the wallet](https://payment-frontend-52916499838.us-central1.run.app/) or [Swagger](https://payment-frontend-52916499838.us-central1.run.app/api-docs/). Swagger sign-in and the collection runner acquire their application tokens automatically. All money, registrations and bank accounts are simulated. This is not a live banking integration.

| Capability | Evidence / remaining action |
| --- | --- |
| Registration, wallet, deposits, payments/refunds, transfers, requests, history, notifications | All three Postman suites; actual request/response evidence in the manual API workflow artifact |
| Distributed tracing | Manual cloud acceptance verifies stored spans and service chain; select spans in Cloud Trace to compare service durations |
| Correlated raw logs | API report links plus stored log export; paginated export reports limits and missing trace evidence honestly |
| Service/API latency, traffic, errors and availability | Eleven cloud dashboards; `OTel Cloud dashboards` verifies every query against stored metrics |
| Wallet outcomes, database snapshot freshness, browser vitals/errors, collector health | `OTel extended acceptance`, dashboard panels and tests |
| Private Cloud Run identity and controlled deployment | Existing WIF, image digest, candidate, exact-revision promotion, convergence and rollback checks remain unchanged |
| API discovery | 52 documented operations across eight apps, bodies/curl/security; direct internal browser execution disabled |
| Alert rules and local firing/recovery | Implemented and locally exercised; **cloud channel delivery remains an activation and verification step** |
| Durable queue across external collector process restart | Local retained-disk drill implemented; **external infrastructure is optional and unprovisioned** |

## Repeatable final checks

All workflows are manual. Run from GitHub Actions on `main`:

1. **OTel final readiness review**: reads current service readiness, deployed immutable image, OTel version, traffic, minimum instances, required permissions and alert/channel configuration. It never changes IAM or deploys. Notification labels/addresses/secrets are excluded. Configuration presence does not prove telemetry storage or alert delivery.
2. **Warm OTel demo services** before a presentation. Warmup is temporary and does not change billing configuration/minimum instances.
3. **Run API collections and document results**, suite `all`, collect logs enabled. It moves only sandbox funds. Download the artifact and open `index.html` for bodies, observed responses, assertions, curl, logs and trace links.
4. **manual-cloud-acceptance** and **OTel extended acceptance**: verify stored cloud signals. They create isolated sandbox activity unless an existing trace is explicitly selected for read-only acceptance.
5. **OTel Cloud dashboards**, `apply=false`, `verify_queries=true`: check samples without replacing dashboards. Historical samples prove storage in that query window; use time ranges and freshness to distinguish old data.
6. **Inspect stored service errors**: review bounded existing failures by timestamp/revision. Expected negative API tests and historical errors must not be treated as a new app outage; investigate fresh unexpected 5xx, collector rejections and export failures.

From repository CMD, `scripts\run-api-tests.cmd` repeats the collections without needing Postman token/environment edits. [Full instructions](API_TESTING_AND_SWAGGER.md).

## Version numbering

The current deployment identifiers are correct and should be retained:

| Identifier | Meaning |
| --- | --- |
| Git commit, such as `3019ecf...` | Exact source. Current Java OTel `service.version` uses the release's source commit |
| Image tag `COMMIT-RUN-ATTEMPT` | Unique build lookup in Artifact Registry |
| Image `@sha256:...` | Exact immutable artifact used for deployment |
| Cloud Run revision `SERVICE-gh-RUN-ATTEMPT` | Unique runnable revision, also used for exact rollback |
| API `/api/v1/...` | Public API contract version, independent of application releases |
| Maven `0.1.0-SNAPSHOT` | Current development package version; not the deployed artifact's immutable identity |

Normal human numbering is also valid: use **`1.0.0`**, then `1.0.1` for compatible fixes, `1.1.0` for compatible features, and `2.0.0` for breaking changes. For service-by-service releases, service-qualified tags such as `auth-service/v1.0.0` avoid suggesting every app was released together. A whole-POC milestone can be `poc-v1.0.0`, with a manifest listing each service's own source/image/revision.

Recommendation for this POC: keep existing automatic commit/digest/revision identifiers, and add a human milestone tag **after final acceptance** if desired. No release tag or package-version change is required to demonstrate OTel. Do not replace image digests with mutable `latest`, reuse revision names, or relabel old telemetry as a new release. Documentation-only commits do not require redeploying every app. A human tag has not been created by this handover.

OpenTelemetry explicitly permits semantic versions and Git hashes for `service.version`: [official conventions](https://opentelemetry.io/docs/specs/semconv/resource/service/). No business versioning change is needed for compliance with that convention.

## What requires an administrator or owner

**Cloud alert delivery** is the remaining operational activation. The selected WIF observability account needs alert-policy create/update permission and an existing enabled/eligible notification channel. Listing/configuration alone does not verify email/webhook delivery. Choose the intended recipient, publish using **OTel Cloud alerts**, then observe a controlled isolated firing and recovery notification. Local Alertmanager delivery is not evidence of GCP channel delivery. Do not cause a real payment outage just to test a notification.

Run this read-only block in **GCP Cloud Shell** and paste the output (it excludes recipient addresses and tokens):

```bash
gcloud config set project project-c9bd3d0e-266f-47bf-852
gcloud beta monitoring channels list \
  --project=project-c9bd3d0e-266f-47bf-852 \
  --format='table(name,type,enabled,verificationStatus)'
gcloud projects get-iam-policy project-c9bd3d0e-266f-47bf-852 \
  --flatten='bindings[].members' \
  --filter='bindings.members:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com' \
  --format='table(bindings.role)'
```

If GitHub `gcp-poc` uses `GCP_OBSERVABILITY_SERVICE_ACCOUNT`, run the role lookup for that account instead. The final readiness artifact reports effective alert permissions for the actual WIF identity and does not rely solely on role names.

Only if that identity lacks publication permission, an administrator can review and run:

```bash
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 \
  --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com \
  --role=roles/monitoring.alertPolicyEditor --condition=None
```

Use the selected observability identity instead of the default deployer if configured. This command changes IAM; it is not executed by any new script/workflow. [Official notification-channel commands](https://docs.cloud.google.com/sdk/gcloud/reference/beta/monitoring/channels/list).

The 22 October readiness inventory found **no notification channels**, and missing `monitoring.alertPolicies.create` / `monitoring.alertPolicies.update`. If you want email alerts, run this once in Cloud Shell using the address you choose. It creates a notification channel, so avoid repeating it if one already exists:

```bash
read -r -p 'Email address for POC alerts: ' POC_ALERT_EMAIL
gcloud beta monitoring channels create \
  --project=project-c9bd3d0e-266f-47bf-852 \
  --display-name='OTel Payment POC' --type=email --enabled \
  --channel-labels="email_address=${POC_ALERT_EMAIL}" \
  --format='value(name)'
unset POC_ALERT_EMAIL
```

Paste only the returned `projects/.../notificationChannels/...` resource name. Complete any verification requested by Cloud Monitoring. Creating a channel alone does not publish policies or prove notification delivery. [Official create command](https://cloud.google.com/sdk/gcloud/reference/beta/monitoring/channels/create).

**Optional, not prerequisites for the basic POC:** persistent external collector host/disk/TLS/secret; extra always-on warm instances and their ongoing cost; production SLO/on-call/retention/capacity/security requirements. Existing Cloud Run queues are bounded in memory, so outage/replacement can lose unexported telemetry. [Activation and durability details](OTEL_REMAINING_FEATURES.md). No new host, IAM grant, notification recipient or always-on instance is silently enabled.

The POC is ready to demonstrate when the repeatable checks pass. Do not describe cloud alert delivery or external durable storage as completed until separately activated and proven.

## Known non-blocking environment warning

The 22 October bounded log review found a Flyway compatibility warning: the Neon database reports PostgreSQL 18.6, while the packaged Flyway build reports tested support through PostgreSQL 17. Database-backed acceptance paths passed, so this was not an observed migration failure. Treat a tested Flyway/PostgreSQL support alignment as dependency maintenance before production use; it is not fixed by hiding the warning. SpringDoc's enabled-endpoint notice is expected for this POC's intentional API documentation. Private-invoker 401/403 and negative-input 400 responses are also deliberately exercised by acceptance tests.

The extended acceptance client now permits 190 seconds for one cold registration/login attempt, matching the deployed 180-second request boundary plus network overhead. It records the registration phase explicitly and never blindly replays an uncertain registration POST. Cold starts are still possible; this is not a low-latency guarantee.
