# Auth login and private Cloud Run verification — 14 October 2026

All changes are local/uncommitted. No deployment, gcloud command, resource creation, IAM/secret change or database migration was executed. Cloud Run IAM/Collector/probe configuration stays unchanged. Business login/payment logic stays unchanged.

## Findings and before/after security

**Confirmed startup-probe defect:** Auth SecurityConfig permitted only the exact `/actuator/health` origin; `/liveness` and `/readiness` fell through the authenticated rule. The custom SecurityErrorHandler generated `AUTHENTICATION_REQUIRED` / `A valid bearer token is required`. The JWT filter is Spring's resource-server bearer filter, not a separate custom JWT OncePerRequestFilter. Merely adding permitAll would not prevent a malformed bearer header from triggering that filter. A highest-priority health-only filter chain now bypasses application bearer processing for `/actuator/health` and descendants, with no blanket actuator permission. All other actuator endpoints (including previously public info) now require JWT or remain unexposed. The HTTP startup/liveness probe remains `/actuator/health/liveness`; no TCP workaround is used.

Login remains public at the application layer, and its bearer-token resolver ignores Google ID tokens supplied by the private gateway. Username/password business authentication still occurs normally. Registration does not exist in this repo; no registration API/public exclusion was invented. Its previous unauthenticated401 behavior remains. Protected business routes keep normal JWT validation. No backend starts trusting browser identity headers instead of signed customer JWTs.

**OUT_OF_SERVICE:** No custom Auth HealthIndicator/ReactiveHealthIndicator was found. Spring availability/readiness can produce OUT_OF_SERVICE when REFUSING_TRAFFIC during startup/shutdown/retirement. A real local HTTP-server test reproduces this exact503 status/body while liveness remains200. That is a demonstrated possible cause, not proof of the historical retired Cloud Run revision's component. The historical component cannot be identified from the provided aggregate response alone. Liveness now explicitly includes only livenessState; readiness includes readinessState and db. Simulated DB failure produces503 readiness/root and200 liveness, so real database failures are not hidden. HealthDiagnostics logs availability state changes and DOWN/OUT_OF_SERVICE/UNKNOWN response statuses/component names without details, credentials or exception messages.

**API Gateway500:** The supplied edge correlation/trace and absence of auth logs do not prove the exact exception. The old implementation hand-fetched metadata tokens and propagated acquisition/allowlist/parse errors without a stable gateway response. The Google auth library now acquires ID tokens through runtime ADC/IdTokenProvider/IdTokenCredentials, lazily (no credentials needed when IAM is disabled). The existing canonical origin allowlist, expiry/audience validation and cache remain. Actual auth route properties bind the configured canonical origin. A token-generation failure now yields503 `BACKEND_AUTHENTICATION_UNAVAILABLE` with a safe message and correlation ID; no stack, exception object, token, URL or secret is returned. Downstream errors are not mislabeled as credential failures.

Login gets Google ID-token Authorization and X-Serverless-Authorization. Business calls retain the validated customer JWT in Authorization and use the Google token in X-Serverless-Authorization. This preserves backend application JWT checks and the existing trusted identity mechanism. RequestContextFilter strips spoofed X-Authenticated-User/X-Customer-Id/X-User-Roles, then copies claims only from an authenticated JwtAuthenticationToken. It does not replace JWT validation with header trust. Correlation/body/traceparent/tracestate/baggage are preserved. Server diagnostic logs identify audience/backend, acquisition outcome, downstream status, correlation and available trace ID, never token values. Google supports X-Serverless-Authorization specifically when application Authorization must be preserved.

**Frontend object Object:** api.js passed arbitrary response objects (or object-valued message fields) to new Error, which coerced them to `[object Object]`. One shared errors.js now implements string-only message/correlation/code precedence, safe fallbacks and sensitive-content filtering. All App/Operations error states use formatApiError; API failures normalize before throwing. Stack traces, bearer/JWT values, credentials, known secret references and internal URLs are not displayed. Correlation appears on its own line. No JSX directly receives an error object.

## Verification results

| Check | Result |
|---|---|
| `mvn -B -pl auth-service -am test` | 11 tests pass (6 actual local HTTP startup/security/health tests; 5 existing controller/service tests) |
| `mvn -B -pl api-gateway -am test` | 17 tests pass: 9 Gateway tests and 8 shared ADC/identity/configuration tests |
| Shared/business-security reactor regression selection | 18 tests pass, including Payment3 and Notification4, plus repeated identity/filter cases |
| Frontend `npm test` | 5 test groups pass, covering nested/string/Error/object input, correlation precedence and sensitive output |
| Frontend `npm run build` | Pass, 37 modules |
| All deployment tests | 52 pass, including private Auth HTTP probe/Collector contract and existing every-service promotion/rollback/no-traffic checks |
| Workflow lint | Pass; optional external ShellCheck unavailable/disabled |
| YAML/JSON validation | 36 reviewed documents parse; all eight manual/main/WIF/build-isolation contracts pass |
| `git diff --check` | Pass |

Maven was run using the IT Java21/Maven installation and the project's local cache. Auth tests start a genuine Tomcat server on a random local port with a controlled DB health indicator and mocked login service; they do not exercise Neon or migrate a database. Shared identity tests mock ADC/token provider and verify exact audience, allowlist and cached token behavior. Gateway route binding/filters are tested locally, including request-body preservation; the real Cloud Run handshake and remote distributed trace were not executed. The localhost isolated test server is closed with its test context.

## Explicit confirmations and practical limits

| Requirement | Confirmation |
|---|---|
| Internal Cloud Run liveness200 | Healthy application's identical HTTP probe path returns200 with absent/malformed JWT in actual local HTTP tests. Expected after deploying Auth; not remotely tested. |
| Anonymous external Auth403 | Cloud Run remains private/enforced in unchanged deployment contract; anonymous external access should stay403 under the supplied IAM state. No anonymous GCP request was made. |
| Login through private Auth | Canonical ADC audience, Google header, request body and actual Auth public login behavior pass local tests. End-to-end Google IAM/login remains a required manual cloud check. |
| Frontend no object Object | Safe formatter returns strings and all UI error paths use it; regression tests and production build pass. |
| Gateway/Auth in one trace | W3C headers are preserved and Java agents/Collector remain. Same distributed trace is expected when both exports are healthy; not verified in Cloud Trace during this task. |

## Remaining manual requirements and deployment order

No new IAM grants or secret values are requested by these changes. Confirm the already supplied runtime/deployer Invoker policy, runtime identity and private IAM enforcement remain. Confirm existing database/JWT secret versions and Collector export permissions. Use existing workflow dynamic canonical discovery; never substitute a display/tag/path URL as audience.

After reviewing/committing these changes and exact-commit CI passes, manually deploy: (1) Auth; check private authenticated health/root/readiness and anonymous rejection; (2) API Gateway; validate login and a protected customer request; (3) Frontend. The shared cloud-runtime ADC implementation is also used by Payment/Gateway adapter: separately deploy those consumers when adopting this change, after their normal regression/acceptance checks. Do not deploy all services together. No workflows were dispatched here.

## Exact post-deployment verification commands (provided only, NOT executed)

Run manually in an authenticated Cloud Shell with permission to impersonate runtime. Keep generated tokens in shell variables and never print them:

```sh
PROJECT=project-c9bd3d0e-266f-47bf-852
REGION=us-central1
RUNTIME=otel-payment-runtime@$PROJECT.iam.gserviceaccount.com
AUTH_URL=$(gcloud run services describe auth-service --project="$PROJECT" --region="$REGION" --format='value(status.url)')
API_URL=$(gcloud run services describe api-gateway --project="$PROJECT" --region="$REGION" --format='value(status.url)')
FRONTEND_URL=$(gcloud run services describe payment-frontend --project="$PROJECT" --region="$REGION" --format='value(status.url)')

# Anonymous external Auth: expected403.
curl -sS -o /dev/null -w '%{http_code}\n' "$AUTH_URL/actuator/health/liveness"

GOOGLE_ID_TOKEN=$(gcloud auth print-identity-token --impersonate-service-account="$RUNTIME" --audiences="$AUTH_URL" --include-email)
# Each expected200/UP for a healthy application and DB.
for path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  curl -sS -w '\nHTTP %{http_code}\n' -H "X-Serverless-Authorization: Bearer $GOOGLE_ID_TOKEN" "$AUTH_URL$path"
done
# Malformed application Authorization must not break the health path: expected200.
curl -sS -o /dev/null -w '%{http_code}\n' -H "X-Serverless-Authorization: Bearer $GOOGLE_ID_TOKEN" -H 'Authorization: Bearer malformed' "$AUTH_URL/actuator/health/liveness"
# Private layer passes, application metrics still needs JWT: expected401.
curl -sS -o /dev/null -w '%{http_code}\n' -H "X-Serverless-Authorization: Bearer $GOOGLE_ID_TOKEN" "$AUTH_URL/actuator/metrics"

TRACE_ID=$(openssl rand -hex 16)
CORRELATION_ID="private-login-$(date +%s)"
LOGIN_RESPONSE=$(curl -sS -H 'Content-Type: application/json' -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "traceparent: 00-$TRACE_ID-0123456789abcdef-01" -H 'tracestate: poc=manual' -H 'baggage: test.name=private-login' \
  --data '{"username":"demo","password":"demo123"}' "$API_URL/api/v1/auth/login")
# Print only safe response fields, never accessToken.
printf '%s' "$LOGIN_RESPONSE" | jq '{tokenType,expiresInSeconds,customerId,roles,code,message,correlationId}'
CUSTOMER_JWT=$(printf '%s' "$LOGIN_RESPONSE" | jq -er '.accessToken')
# API Gateway should enforce customer JWT: expected401 without it.
curl -sS -o /dev/null -w '%{http_code}\n' "$API_URL/api/v1/customers"
# Expected200 if Customer is bootstrapped/healthy and correctly authorized.
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $CUSTOMER_JWT" "$API_URL/api/v1/customers"
curl -sS -o /dev/null -w '%{http_code}\n' "$FRONTEND_URL/health"

# Correlated request logs; inspect both API Gateway and Auth under the same trace.
gcloud logging read "resource.type=\"cloud_run_revision\" AND (resource.labels.service_name=\"api-gateway\" OR resource.labels.service_name=\"auth-service\") AND trace=\"projects/$PROJECT/traces/$TRACE_ID\"" --project="$PROJECT" --limit=30 --format='table(timestamp,resource.labels.service_name,severity,httpRequest.status)'
printf 'Trace: https://console.cloud.google.com/traces/explorer?project=%s\nTrace ID: %s\nCorrelation: %s\n' "$PROJECT" "$TRACE_ID" "$CORRELATION_ID"
unset GOOGLE_ID_TOKEN CUSTOMER_JWT LOGIN_RESPONSE
```

In frontend, verify an incorrect password produces a readable message/correlation, not an object. For a deliberate gateway token failure, inspect `WORKLOAD_TOKEN_FAILED` and BACKEND_AUTHENTICATION_UNAVAILABLE without changing production IAM during normal acceptance. Confirm Auth availability logs show ACCEPTING_TRAFFIC; a DB failure should affect readiness, never be fixed by making the service public. Cloud Trace export may lag request completion.

References: [Cloud Run two authentication headers](https://docs.cloud.google.com/run/docs/authenticating/service-to-service), [Google ADC ID-token sample](https://docs.cloud.google.com/run/docs/samples/cloudrun-service-to-service-auth).

## Modified files

- `api-gateway/src/main/java/com/srinu/otelpoc/gateway/filter/CloudRunIdentityFilter.java`
- `api-gateway/src/main/java/com/srinu/otelpoc/gateway/filter/RequestContextFilter.java`
- `api-gateway/src/test/java/com/srinu/otelpoc/gateway/filter/AuthRouteConfigurationTest.java`
- `api-gateway/src/test/java/com/srinu/otelpoc/gateway/filter/CloudRunIdentityFilterTest.java`
- `api-gateway/src/test/java/com/srinu/otelpoc/gateway/filter/RequestContextFilterTest.java`
- `auth-service/src/main/java/com/srinu/otelpoc/auth/config/HealthDiagnostics.java`
- `auth-service/src/main/java/com/srinu/otelpoc/auth/config/SecurityConfig.java`
- `auth-service/src/main/resources/application.yml`
- `auth-service/src/test/java/com/srinu/otelpoc/auth/config/AuthSecurityIntegrationTest.java`
- `cloud-runtime/pom.xml`
- `cloud-runtime/src/main/java/com/srinu/otelpoc/cloud/CloudRunIdentity.java`
- `cloud-runtime/src/test/java/com/srinu/otelpoc/cloud/CloudRunAutoConfigurationTest.java`
- `cloud-runtime/src/test/java/com/srinu/otelpoc/cloud/CloudRunIdentityTest.java`
- `frontend/package.json`
- `frontend/src/App.jsx`
- `frontend/src/OperationsPanel.jsx`
- `frontend/src/api.js`
- `frontend/src/errors.js`
- `frontend/src/errors.test.js`
- `frontend/src/styles.css`
- `scripts/cloud/test_auth_probe_contract.py`
- `docs/AUTH_LOGIN_CLOUD_RUN_REVIEW.md` (this report)

## Gateway startup follow-up - 14 October 2026

The subsequent Gateway candidate `api-gateway-gh-38554320481-1` failed before HTTP startup. The supplied Cloud Run stack trace and a full local startup test both confirm `jsonToGRPCFilterFactory -> grpcSslConfigurer -> NoClassDefFoundError: io/grpc/netty/NettyChannelBuilder`. The ADC library's transitive gRPC API classes activated Gateway's optional JSON-to-gRPC feature without its Netty transport. This application routes HTTP and does not use that feature. `spring.cloud.gateway.server.webflux.filter.json-to-grpc.enabled=false` disables it while retaining ADC identity authentication, all routes, JWT enforcement and the Collector. A real Gateway startup regression test with IAM enabled now checks liveness/readiness200 and that the unused filter is absent.

The 18 Gateway/shared tests pass locally with this fix. Frontend deployment run38554543068 passed candidate and production health and promoted its exact revision. Read-only diagnostic workflow38555032114 can read revision conditions, but log access was denied because github-deployer lacks log-view permissions; no IAM grant was added. The user supplied the failed container stack instead. The diagnostic workflow is manual/main/WIF only and never modifies Cloud Run resources.

Reference: [Gateway JSON-to-gRPC configuration](https://docs.spring.io/spring-cloud-gateway/reference/configprops.html).
