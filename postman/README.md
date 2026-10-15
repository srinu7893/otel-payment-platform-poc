# Payment Platform in Postman

Verified against the deployed sandbox on 2026-10-15 with Newman: **63 requests, 94 assertions, zero failures**. The collection tests existing features; it does not require an application deployment.

Import these files into Postman:

1. `Payment-Platform.postman_collection.json`
2. `Cloud.postman_environment.json` for the deployed app, or `Local.postman_environment.json` for the existing local stack.

Select **Payment Platform - Cloud** in the environment selector. Its `base_url` is `https://payment-frontend-52916499838.us-central1.run.app/api/v1` (no trailing slash). Requests go through the public frontend/API gateway; private Cloud Run services remain private. Local uses `http://127.0.0.1:28088/api/v1`; start the local stack first, or change the port to your gateway port.

## Run the complete journey

Open the collection, choose **Run**, select all seven numbered folders, set **Iterations = 1**, and run them in order. Use the desktop app or Desktop Agent for localhost access. This deliberately creates two unique sandbox users and deposits INR 20 and INR 10 in simulated funds. No real bank or card is charged.

The first request **Initialize fresh users** generates usernames/passwords and resets the journey variables. Its HTTP 401 is expected: that request also checks that anonymous wallet access is denied. Do not run Initialize again in the middle of a journey.

Registration automatically saves each user's token, customer ID and account number as collection variables. Later requests automatically save payment, transfer, refund/deposit retry keys and request IDs. You do not need to paste bearer tokens. The requests explicitly select the sender, receiver or support identity so payer approval and ownership checks use the right account.

| Folder | What you can test |
| --- | --- |
| 01 Register and login | New accounts, chosen credentials, invalid login |
| 02 Accounts and demo funds | Profile, balances, deposits, replay protection, recipient lookup, invalid amounts, anonymous access |
| 03 Merchant payment and refund | Payment, details/history, refund, safe replays, ownership, invalid IDs/filters |
| 04 Transfers | Send money, details/history, sender/receiver balances, replay, spoofing/self-transfer/currency rejection |
| 05 Money requests | Creation, payer approval/replay, decline, cancel, final balances |
| 06 Activity and notifications | Bank ledger, incoming credits, pagination, event filters, profile ownership |
| 07 Support operations | Support login, service status/latency, transaction lookup, customer list, forbidden wallet mutation |

Expected final balances for the unmodified complete journey: **sender INR 19, receiver INR 11**. The exact-balance assertions assume fresh accounts and the supplied order/amounts. Notifications are asynchronous; resend the notification GET requests after a few seconds if the list is initially empty. Inspect each service's status in the health response; a successful HTTP response alone does not mean every dependency is UP.

## Send individual requests

After initialization/registration, select a request, inspect its body and click **Send**. View the **Test Results** tab for assertions, and **Body** for transaction status, account details and error/correlation IDs. Generated usernames/passwords are in the collection's **Variables** tab; use them to sign into the frontend too. Support uses the existing sandbox `support / support123` credentials from the selected environment. Change those environment values if your deployment uses different credentials.

For your own existing login, replace the Login request's username/password variables, send Login, then use profile/balance/history reads. Do not run zero-opening/exact-balance acceptance assertions against an existing account. Amounts and destinations in the collection are intentionally fixed for a reproducible test; change them for exploratory testing and update the expected balances accordingly.

## Safe retries and cold starts

- A deposit/payment/transfer/refund gets a saved idempotency key on its first send. Resending that unchanged request or its **Replay** request reuses the key. If a response is lost, keep that key and check details/history before retrying. Do not substitute a new UUID to retry an uncertain transaction.
- For an intentional new transaction with the same create request, first confirm the old transaction's final outcome, then clear only its collection variable (`deposit_key`, `receiver_deposit_key`, `payment_key`, `transfer_key` or `refund_key`). The next Send creates a new key. Never reuse an old key with a changed payload.
- Registration can return HTTP 503 `REGISTRATION_PENDING` while dependencies start. Resend the same registration request with the same details; do not run Initialize. After recovery, restart the complete acceptance run with fresh users if you need its zero-opening balance assertions.
- If a money movement returns `PROCESSING` or `RECONCILIATION_REQUIRED`, read its details until settled. The acceptance tests expect completed sandbox operations and flag pending results for investigation; they do not automatically resubmit money movement.
- Negative tests intentionally expect 400/401/403/404. A green assertion on one of those requests means the rejection worked.
- Tokens and generated passwords are runtime collection variables. Clear them before exporting/sharing the collection; do not sync or commit populated credentials. The committed collection/environment files contain no personal access tokens. This collection does not directly call private provisioning/admin-delete endpoints.

## Run from CMD with Newman

```cmd
npm install --prefix artifacts/postman-tools --no-save newman
node artifacts\postman-tools\node_modules\newman\bin\newman.js run postman\Payment-Platform.postman_collection.json -e postman\Cloud.postman_environment.json --timeout-request 90000 --delay-request 250 --bail
```

On a managed Windows machine where Node does not trust the corporate HTTPS certificate, use a Node version that supports `node --use-system-ca` and add that flag before the Newman path. Keep TLS verification enabled. Alternatively configure Node/Postman with the IT-provided CA certificate.

The collection is manual testing material. It adds no deployment-on-push trigger and does not change application logic. Real bank linking, real funding, KYC, password recovery and external UPI/card rails remain outside this sandbox.
