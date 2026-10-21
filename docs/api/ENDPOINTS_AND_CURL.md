# API endpoint and curl catalog

Generated from Java controller handlers by `python scripts/build-api-docs.py`. Schema response objects that are not records are intentionally generic; actual observed JSON is in the automated execution report. This catalog is documentation coverage, not a claim that every operation has been executed.

Public entry: `https://payment-frontend-52916499838.us-central1.run.app`. Public Swagger UI: `/api-docs/`. Frontend is a UI/reverse proxy, not a separate business API service.

Public calls use application JWTs. Use Swagger Sign in or the automatic runner; no token copying is needed there. Private direct calls additionally need a Google ID token with canonical service audience and invoker permission. Their browser Try it out is disabled. Internal mutation endpoints must not be treated as end-user payment entry points.

| Service | Method | Path | Required access |
| --- | --- | --- | --- |
| api-gateway | POST | `/api/v1/telemetry/rum` | Anonymous with allowed Origin and bounded body |
| api-gateway | GET | `/api/v1/ops/health` | Bearer JWT: SUPPORT or ADMIN |
| auth-service | POST | `/api/v1/auth/login` | Anonymous |
| auth-service | POST | `/api/v1/auth/register` | Anonymous |
| customer-service | GET | `/api/v1/customers` | Bearer JWT: SUPPORT or ADMIN |
| customer-service | GET | `/api/v1/customers/{id}` | Bearer JWT: owner, SUPPORT or ADMIN |
| customer-service | POST | `/api/v1/customers` | Bearer JWT: ADMIN |
| customer-service | PUT | `/api/v1/customers/{id}` | Bearer JWT: ADMIN |
| customer-service | DELETE | `/api/v1/customers/{id}` | Bearer JWT: ADMIN |
| customer-service | PUT | `/internal/onboarding/customers/{id}` | Cloud Run invoker identity; trusted service/operator only |
| payment-service | GET | `/api/v1/money-requests` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/money-requests` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/money-requests/{id}/pay` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/money-requests/{id}/decline` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/money-requests/{id}/cancel` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/payments` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/payments/{id}` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/payments` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | POST | `/api/v1/payments/{id}/cancel` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | POST | `/api/v1/payments/{id}/refunds` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/payments/{id}/refund` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | POST | `/api/v1/transfers` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/transfers/{id}` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/transfers` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| payment-service | GET | `/api/v1/wallet` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | GET | `/api/v1/wallet/recipients/{account}` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | GET | `/api/v1/wallet/activity` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| payment-service | POST | `/api/v1/wallet/deposits` | Bearer JWT: CUSTOMER; ownership/payer rules apply |
| gateway-service | POST | `/api/v1/authorizations` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | POST | `/api/v1/refund-authorizations` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | POST | `/api/v1/transfer-authorizations` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | GET | `/api/v1/wallet/{account}` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | GET | `/api/v1/wallet/recipients/{account}` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | GET | `/api/v1/wallet/{account}/activity` | Cloud Run invoker identity; trusted service/operator only |
| gateway-service | POST | `/api/v1/wallet/{account}/deposits` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/admin/accounts` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | GET | `/api/v1/admin/accounts/{accountNumber}` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | GET | `/api/v1/admin/accounts` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | PUT | `/api/v1/admin/accounts/{accountNumber}` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/admin/accounts/{accountNumber}/deactivate` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/admin/accounts/{accountNumber}/activate` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/bank/debits` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/bank/refunds` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | PUT | `/internal/onboarding/accounts/{number}` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/bank/transfers` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | GET | `/api/v1/bank/wallet/{account}` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | GET | `/api/v1/bank/wallet/recipients/{account}` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | GET | `/api/v1/bank/wallet/{account}/activity` | Cloud Run invoker identity; trusted service/operator only |
| mock-bank-service | POST | `/api/v1/bank/wallet/{account}/deposits` | Cloud Run invoker identity; trusted service/operator only |
| notification-service | GET | `/api/v1/notifications` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| notification-service | GET | `/api/v1/notifications/{id}` | Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply |
| frontend | GET | `/health` | Anonymous frontend health check |

## api-gateway: POST /api/v1/telemetry/rum

Anonymous with allowed Origin and bounded body.

Source: `api-gateway/src/main/java/com/srinu/otelpoc/gateway/ops/BrowserTelemetryController.java`.

Filled request body:
```json
{
  "name": "PAGE_VIEW",
  "value": 1
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/telemetry/rum" ^
  --header "Origin: https://payment-frontend-52916499838.us-central1.run.app" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/api_gateway_post_api_v1_telemetry_rum.json"
```


## api-gateway: GET /api/v1/ops/health

Bearer JWT: SUPPORT or ADMIN.

Source: `api-gateway/src/main/java/com/srinu/otelpoc/gateway/ops/OperationsController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/ops/health" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## auth-service: POST /api/v1/auth/login

Anonymous.

Source: `auth-service/src/main/java/com/srinu/otelpoc/auth/api/AuthController.java`.

Filled request body:
```json
{
  "username": "demo",
  "password": "demo123"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/auth/login" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/auth_service_post_api_v1_auth_login.json"
```


## auth-service: POST /api/v1/auth/register

Anonymous.

Source: `auth-service/src/main/java/com/srinu/otelpoc/auth/api/RegistrationController.java`.

Filled request body:
```json
{
  "username": "postman_ready_2142f04f05",
  "password": "Postman-ready-724a806f656647d4!9",
  "name": "Postman Ready Customer",
  "email": "postman_ready_2142f04f05@example.com"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/auth/register" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/auth_service_post_api_v1_auth_register.json"
```


## customer-service: GET /api/v1/customers

Bearer JWT: SUPPORT or ADMIN.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/CustomerController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/customers" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## customer-service: GET /api/v1/customers/{id}

Bearer JWT: owner, SUPPORT or ADMIN.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/CustomerController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/customers/demo-customer" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## customer-service: POST /api/v1/customers

Bearer JWT: ADMIN.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/CustomerController.java`.

Filled request body:
```json
{
  "name": "Demo Customer",
  "email": "demo@example.com",
  "accountNumber": "ACC1001"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/customers" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/customer_service_post_api_v1_customers.json"
```


## customer-service: PUT /api/v1/customers/{id}

Bearer JWT: ADMIN.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/CustomerController.java`.

Filled request body:
```json
{
  "name": "Demo Customer",
  "email": "demo@example.com",
  "accountNumber": "ACC1001"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request PUT "https://payment-frontend-52916499838.us-central1.run.app/api/v1/customers/demo-customer" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/customer_service_put_api_v1_customers_id.json"
```


## customer-service: DELETE /api/v1/customers/{id}

Bearer JWT: ADMIN.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/CustomerController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request DELETE "https://payment-frontend-52916499838.us-central1.run.app/api/v1/customers/demo-customer" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## customer-service: PUT /internal/onboarding/customers/{id}

Cloud Run invoker identity; trusted service/operator only.

Source: `customer-service/src/main/java/com/srinu/otelpoc/customer/api/OnboardingController.java`.

Filled request body:
```json
{
  "name": "Demo Customer",
  "email": "demo@example.com",
  "accountNumber": "ACCAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe customer-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request PUT "$SERVICE_URL/internal/onboarding/customers/demo-customer" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/customer_service_put_internal_onboarding_customers_id.json"
```


## payment-service: GET /api/v1/money-requests

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/MoneyRequestController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/money-requests" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/money-requests

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/MoneyRequestController.java`.

Filled request body:
```json
{
  "idempotencyKey": "015856b8-0910-4b29-88bd-2b68a097f130",
  "payerAccount": "ACC2001",
  "amount": 1,
  "note": "Postman manual shared coffee"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/money-requests" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/payment_service_post_api_v1_money_requests.json"
```


## payment-service: POST /api/v1/money-requests/{id}/pay

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/MoneyRequestController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/money-requests/a2f20b5d-375e-3c74-a430-789ebf36a008/pay" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/money-requests/{id}/decline

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/MoneyRequestController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/money-requests/a2f20b5d-375e-3c74-a430-789ebf36a008/decline" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/money-requests/{id}/cancel

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/MoneyRequestController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/money-requests/a2f20b5d-375e-3c74-a430-789ebf36a008/cancel" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/payments

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

Filled request body:
```json
{
  "idempotencyKey": "c4373eeb-7192-49a4-ab8b-093a042e3ae4",
  "accountNumber": "ACC1001",
  "merchant": "Postman manual coffee purchase",
  "amount": 2
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/payment_service_post_api_v1_payments.json"
```


## payment-service: GET /api/v1/payments/{id}

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments/747c8a7d-93e2-4d6a-bb59-753fc57a7712" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: GET /api/v1/payments

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/payments/{id}/cancel

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments/747c8a7d-93e2-4d6a-bb59-753fc57a7712/cancel" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/payments/{id}/refunds

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

Filled request body:
```json
{
  "idempotencyKey": "43426b29-3ab0-5dbb-8694-e5b2b4dd06f4"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments/747c8a7d-93e2-4d6a-bb59-753fc57a7712/refunds" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/payment_service_post_api_v1_payments_id_refunds.json"
```


## payment-service: GET /api/v1/payments/{id}/refund

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/PaymentController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/payments/747c8a7d-93e2-4d6a-bb59-753fc57a7712/refund" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/transfers

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/TransferController.java`.

Filled request body:
```json
{
  "idempotencyKey": "3b6ed1d5-50d9-4a7a-bac6-45cb356fabab",
  "senderAccount": "ACC1001",
  "receiverAccount": "ACC2001",
  "amount": 1,
  "currency": "INR"
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/transfers" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/payment_service_post_api_v1_transfers.json"
```


## payment-service: GET /api/v1/transfers/{id}

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/TransferController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/transfers/fc072473-b6fe-4bc0-a4a8-746cfd7b4abf" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: GET /api/v1/transfers

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/TransferController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/transfers" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: GET /api/v1/wallet

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/WalletController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/wallet" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: GET /api/v1/wallet/recipients/{account}

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/WalletController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/wallet/recipients/ACC1001" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: GET /api/v1/wallet/activity

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/WalletController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/wallet/activity" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## payment-service: POST /api/v1/wallet/deposits

Bearer JWT: CUSTOMER; ownership/payer rules apply.

Source: `payment-service/src/main/java/com/srinu/payments/payment/api/WalletController.java`.

Filled request body:
```json
{
  "idempotencyKey": "aa049b81-b328-4ea6-b36c-789e688d6e94",
  "amount": 10
}
```

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request POST "https://payment-frontend-52916499838.us-central1.run.app/api/v1/wallet/deposits" ^
  --header "Authorization: Bearer %PAYMENT_JWT%" ^
  --header "Content-Type: application/json" ^
  --data-binary "@docs/api/bodies/payment_service_post_api_v1_wallet_deposits.json"
```


## gateway-service: POST /api/v1/authorizations

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/AuthorizationController.java`.

Filled request body:
```json
{
  "paymentId": "6f443afb-b6ee-5ae2-8ec8-39d05528729c",
  "accountNumber": "ACC1001",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/authorizations" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/gateway_service_post_api_v1_authorizations.json"
```


## gateway-service: POST /api/v1/refund-authorizations

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/RefundAuthorizationController.java`.

Filled request body:
```json
{
  "refundId": "726c9142-724e-5376-83a0-9a138f444cf1",
  "originalPaymentId": "747c8a7d-93e2-4d6a-bb59-753fc57a7712",
  "accountNumber": "ACC1001",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/refund-authorizations" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/gateway_service_post_api_v1_refund_authorizations.json"
```


## gateway-service: POST /api/v1/transfer-authorizations

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/TransferAuthorizationController.java`.

Filled request body:
```json
{
  "paymentId": "98c4faff-533d-5b05-8da8-a5aca8395503",
  "senderAccount": "ACC1001",
  "receiverAccount": "ACC2001",
  "amount": 1,
  "currency": "INR"
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/transfer-authorizations" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/gateway_service_post_api_v1_transfer_authorizations.json"
```


## gateway-service: GET /api/v1/wallet/{account}

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/wallet/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## gateway-service: GET /api/v1/wallet/recipients/{account}

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/wallet/recipients/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## gateway-service: GET /api/v1/wallet/{account}/activity

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/wallet/ACC1001/activity" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## gateway-service: POST /api/v1/wallet/{account}/deposits

Cloud Run invoker identity; trusted service/operator only.

Source: `gateway-service/src/main/java/com/srinu/payments/gateway/api/WalletController.java`.

Filled request body:
```json
{
  "idempotencyKey": "abfa237c-fae1-55b1-bae7-e1c328332917",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe gateway-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/wallet/ACC1001/deposits" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/gateway_service_post_api_v1_wallet_account_deposits.json"
```


## mock-bank-service: POST /api/v1/admin/accounts

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Filled request body:
```json
{
  "accountNumber": "ACC1001",
  "customerName": "Demo Customer",
  "balance": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/admin/accounts" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_post_api_v1_admin_accounts.json"
```


## mock-bank-service: GET /api/v1/admin/accounts/{accountNumber}

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/admin/accounts/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: GET /api/v1/admin/accounts

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/admin/accounts" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: PUT /api/v1/admin/accounts/{accountNumber}

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Filled request body:
```json
{
  "accountNumber": "ACC1001",
  "customerName": "Demo Customer",
  "balance": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request PUT "$SERVICE_URL/api/v1/admin/accounts/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_put_api_v1_admin_accounts_accountNumber.json"
```


## mock-bank-service: POST /api/v1/admin/accounts/{accountNumber}/deactivate

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/admin/accounts/ACC1001/deactivate" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: POST /api/v1/admin/accounts/{accountNumber}/activate

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/AccountAdminController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/admin/accounts/ACC1001/activate" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: POST /api/v1/bank/debits

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/BankController.java`.

Filled request body:
```json
{
  "paymentId": "d71b8b20-6775-51eb-8844-471787817fdc",
  "accountNumber": "ACC1001",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/bank/debits" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_post_api_v1_bank_debits.json"
```


## mock-bank-service: POST /api/v1/bank/refunds

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/BankController.java`.

Filled request body:
```json
{
  "refundId": "a03a8d6a-8577-5704-93fc-c614df8c7a32",
  "originalPaymentId": "747c8a7d-93e2-4d6a-bb59-753fc57a7712",
  "accountNumber": "ACC1001",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/bank/refunds" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_post_api_v1_bank_refunds.json"
```


## mock-bank-service: PUT /internal/onboarding/accounts/{number}

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/OnboardingController.java`.

Filled request body:
```json
{
  "name": "Demo Customer"
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request PUT "$SERVICE_URL/internal/onboarding/accounts/ACCAAAAAAAAAAAAAAAAAAAAAAAA" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_put_internal_onboarding_accounts_number.json"
```


## mock-bank-service: POST /api/v1/bank/transfers

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/TransferController.java`.

Filled request body:
```json
{
  "paymentId": "40ea03d9-30bf-5c82-8035-9648f010df3d",
  "senderAccount": "ACC1001",
  "receiverAccount": "ACC2001",
  "amount": 1,
  "currency": "INR"
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/bank/transfers" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_post_api_v1_bank_transfers.json"
```


## mock-bank-service: GET /api/v1/bank/wallet/{account}

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/bank/wallet/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: GET /api/v1/bank/wallet/recipients/{account}

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/bank/wallet/recipients/ACC1001" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: GET /api/v1/bank/wallet/{account}/activity

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/WalletController.java`.

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request GET "$SERVICE_URL/api/v1/bank/wallet/ACC1001/activity" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"
```


## mock-bank-service: POST /api/v1/bank/wallet/{account}/deposits

Cloud Run invoker identity; trusted service/operator only.

Source: `mock-bank-service/src/main/java/com/srinu/payments/bank/api/WalletController.java`.

Filled request body:
```json
{
  "idempotencyKey": "e92c50dc-d80b-53c0-9279-110017db7192",
  "amount": 1
}
```

Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):
```bash
SERVICE_URL=$(gcloud run services describe mock-bank-service --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")
CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)
curl --request POST "$SERVICE_URL/api/v1/bank/wallet/ACC1001/deposits" \
  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN" \
  --header "Content-Type: application/json" \
  --data-binary "@docs/api/bodies/mock_bank_service_post_api_v1_bank_wallet_account_deposits.json"
```


## notification-service: GET /api/v1/notifications

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `notification-service/src/main/java/com/srinu/payments/notification/api/NotificationController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/notifications" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## notification-service: GET /api/v1/notifications/{id}

Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply.

Source: `notification-service/src/main/java/com/srinu/payments/notification/api/NotificationController.java`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/api/v1/notifications/36856c77-cf08-55b6-9dbc-bcb7d1c45e6e" ^
  --header "Authorization: Bearer %PAYMENT_JWT%"
```


## frontend: GET /health

Anonymous frontend health check.

Source: `frontend/cloud-run.conf.template`.

CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):
```cmd
curl.exe --request GET "https://payment-frontend-52916499838.us-central1.run.app/health"
```
