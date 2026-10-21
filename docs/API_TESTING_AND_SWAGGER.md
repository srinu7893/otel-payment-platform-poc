# Automatic API testing, request/response evidence and Swagger

## Run every collection from CMD

From the repository root:

```cmd
scripts\run-api-tests.cmd
```

This runs **all 99 requests**: live data (31), isolated acceptance (63), and browser telemetry (5). It handles login, registration, body values and runtime tokens. No manual Postman environment selection or token pasting is needed. The isolated suite automatically loads its committed Cloud configuration internally. Money-changing requests are not blindly retried.

The installed Node 24 supports `--use-system-ca`, so certificate verification uses the managed Windows trust store. If Newman is absent in a fresh checkout, install it once:

```cmd
npm install --prefix artifacts/postman-tools --no-save newman@6.2.3
```

Optional commands:

```cmd
scripts\run-api-tests.cmd --suite=live
scripts\run-api-tests.cmd --suite=acceptance
scripts\run-api-tests.cmd --suite=rum
scripts\run-api-tests.cmd --open
```

Open **artifacts/api-tests/latest/index.html** after execution. The timestamped folder beside `latest` preserves each run. The command returns a nonzero exit code for failed assertions, network/script failures, or requests that were skipped without assertion evidence. Expected 400/401/403/404 cases pass only when their assertions confirm the rejection. Login helper calls are counted separately from collection requests.

For individual Postman Send operations, inspect **Body**, **Test Results**, and **Visualize** for that request. Run the command or cloud workflow whenever you want a new persistent documentation/report snapshot. Response timing in the report is client round-trip time; use the linked trace waterfall for individual downstream-service spans.

## What is documented after each run

| Artifact | Contents |
| --- | --- |
| `index.html` | Searchable results; expand a request for its actual response, assertions, timing, curl, correlation ID and investigation links |
| `api-tested.md` | Tested API request/response documentation generated from the run |
| `results.json` | Sanitized machine-readable execution evidence, source commit and working-tree state |
| `live.xml`, `acceptance.xml`, `rum.xml` | JUnit test results for the selected suites |
| `bodies/*.json` | Sanitized request bodies referenced by audit curl commands |
| `api-logs.md`, `api-logs.json` | Actual stored API logs when collected by the cloud workflow |

Passwords, bearer tokens, cookies and credential fields are redacted from reports. Do not use redacted audit login bodies as credentials: ready-to-use sandbox request bodies are in **docs/api/bodies**, and the live collection contains its filled bodies. Responses are actual observations, not fabricated examples. To bound report size, arrays contain at most ten items and strings at most 6,000 characters; omitted data is labeled. A recorded `COMPLETED`, `REFUNDED`, `PAID`, or rejected state describes that observation, not an invariant future status.

Repeated live collection sends replay its saved idempotency keys. The isolated suite creates fresh test users/funds for each deliberate run. Browser telemetry tests submit synthetic measurements and error counts. All funds are simulated INR.

## Run in GitHub and include actual stored API logs

Open **Actions → Run API collections and document results → Run workflow** on main. Leave `suite=all` and `collect_logs=true` to run collections and fetch correlated Cloud Logging entries using the existing WIF observability identity. Download the `api-collection-tests-RUN_ID` artifact and open `index.html` after extracting it.

This workflow is manual. It does not deploy, schedule traffic, change IAM, or make private services public. Existing Logs Viewer / Trace User / Monitoring Viewer access is checked before testing when log collection is selected. No GCP JSON key or personal token needs to be pasted.

Each request links to Cloud Logging and Cloud Trace using the **accepted response trace ID**. Sign into Google Cloud with viewing permissions to open those links. The cloud collector exports bounded sanitized stored log entries to the artifact. It explicitly marks `NO_LOGS_OBSERVED` when a trace has no stored application log in the sampled window. A returned trace ID or working link is not proof that a log was stored. Some security rejections have no application log event. Log evidence is limited to 1,000 entries and reports when more entries exist.

For an already authenticated local gcloud session, collect logs after the CMD run:

```cmd
artifacts\native\python\python.exe scripts\cloud\api_test_logs.py
```

## Swagger for each application

Open the frontend's **API explorer** link or:

```text
https://payment-frontend-52916499838.us-central1.run.app/api-docs/
```

The explorer provides the combined public API and service catalogs for all eight applications: frontend, API gateway, auth, customer, payment, gateway adapter, mock bank and notification. The generated catalog contains **52 operations**: all 51 Java controller handlers plus the frontend health route. Management endpoints supplied by Spring Actuator are described separately below. This is documentation coverage, not a claim that all 52 operations have been executed.

Select a service, expand an endpoint, and inspect method/path, required role/token, path/query parameters, required body fields, examples, response schema and generated curl. Use **Sign in for API requests** with the filled demo credentials; the explorer obtains the application JWT in page memory. No token copying is required. Change the username/password to receiver, support or admin when testing that role. Tokens are not stored in localStorage and can be cleared. A successful Swagger Login operation also authorizes subsequent calls automatically.

The public catalogs execute only through the current frontend origin. Private bank, gateway-adapter and onboarding catalogs are **documentation-only** in the browser: Try it out is disabled and no public route was added. Their documented Cloud Shell curl recipes require an existing Cloud Run invoker identity and audience-bound Google ID token. They are trusted service/operator APIs and must not be used as end-user payment entry points.

Swagger previously existed only at API gateway with an incomplete static contract. The new central explorer hosts the complete per-service contracts; it does **not** install a separate springdoc runtime on every backend. The gateway's existing `/openapi.yaml` contract is also regenerated for the public business API.

## Tokens and direct service access

| Entry point | Required credentials |
| --- | --- |
| Frontend / API gateway public login/register | Filled username/password; no application JWT |
| Public wallet/payment/customer/notification requests | Application JWT, acquired automatically by the runner or Swagger Sign in; required roles/ownership remain enforced |
| Private auth login/register | Google ID token for canonical auth-service URL, plus login/registration body |
| Private payment/notification service APIs | Google ID token for that service, plus application JWT |
| Private customer/gateway/mock-bank trusted APIs | Google ID token and invoker permission; customer ownership checks normally happen at the public edge/payment service |
| RUM intake | Allowed Origin and bounded name/value body; no JWT |

The application JWT and Google Cloud Run ID token are different credentials. Direct private-service examples use `X-Serverless-Authorization: Bearer GOOGLE_ID_TOKEN`, leaving `Authorization` available for the application JWT where required. Discover `status.url` using gcloud rather than guessing the ID-token audience. No private invoker IAM is changed by this work.

All endpoint/body/curl examples are in **[ENDPOINTS_AND_CURL.md](api/ENDPOINTS_AND_CURL.md)**. The body JSON files are already populated. Raw curl for protected endpoints requires a fresh application token in `PAYMENT_JWT`; use the runner/Swagger when you want authentication handled automatically. Audit curl in execution reports intentionally omits secrets.

## Operational endpoints and coverage limits

Spring services expose `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` and configured management endpoints. Direct Cloud Run access still follows the service's invoker boundary. Frontend `/health` is public. Use support/admin `/api/v1/ops/health` for aggregate service health through the app. The API catalog is generated from application controller mappings, not a runtime discovery promise for every Actuator endpoint.

Private debit/refund/provisioning/admin writes are documented but are not automatically fired at shared accounts. The acceptance suite tests their normal application paths; stored distributed traces show downstream services. An untested operation has no fabricated response or log evidence. The HTML report lists the exact requests/assertions that ran.

## Maintain the documentation

```cmd
artifacts\native\python\python.exe scripts\build-api-docs.py
artifacts\native\python\python.exe -m unittest discover -s scripts -p test_api_docs.py
node --test scripts\api-report.test.mjs
```

The generator reads controller routes, request records and validation constraints. Non-record response entities remain honest generic objects; actual response JSON is in the run report. CI checks generation drift and report redaction. The Swagger assets are bundled locally, without a CDN or external schema validator. Only documentation/frontend assets and the gateway's static contract require deployment; business logic is unchanged.
