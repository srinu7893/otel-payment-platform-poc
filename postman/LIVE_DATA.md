# Live data — import one collection and Send

Import **Live-Data.postman_collection.json**. Choose **Payment Platform - Live Data - Ready to Send (No Environment)**. Replace your older imported copy. No environment, copied bearer token, URL substitution, or body editing is required. Even a stale selected localhost environment is ignored.

Every saved URL uses the actual public entry point:

```text
https://payment-frontend-52916499838.us-central1.run.app/api/v1
```

The frontend/API gateway calls private Cloud Run services with its runtime identity. Do not replace these URLs with private service URLs. Direct mock-bank access was verified to return HTTP 403; calls through the frontend succeeded. No private service was made public.

Verified on 21 October 2026: all 31 collection requests passed 68 assertions with no environment; the repeated run with a stale localhost environment also passed 68 assertions. All 20 read requests passed individually without setup/create steps. Automatic provisioning of missing customers passed. The CMD curl journey passed 20 checks, including persisted transfers and conserved balances.

## What to click

1. **00 - Login and registration** has filled demo/receiver login bodies, a filled new-customer registration, and its matching login. Registration creates a sandbox bank account with zero balance. The new example customer was created during verification, so its login is already usable. It is separate from the shared transaction accounts.
2. **01 - Fetch existing data immediately** reads actual profiles, balances, ledger, payment/refund/transfer details, requests and notifications. Send any request individually; authentication is automatic.
3. **02 - Create and retrieve new transactions** has filled deposit/payment/transfer/request/refund bodies. Run in order to fund both accounts, create transactions, approve a request, refund a purchase, and fetch updated data. Created IDs are saved inside the collection. Retain collection variable values after a Runner run to fetch those latest IDs afterward.

Transaction accounts: **demo / demo123 / ACC1001** and **receiver / receiver123 / ACC2001**. The prerequest script obtains fresh tokens in memory and reads actual profiles. If a sandbox login returns 401, it attempts registration and uses the assigned account. Provisioning failure skips the business request. On a completely reset database, run the create folder before fetching transaction details referring to old records.

All saved URLs and JSON bodies contain concrete values, including account numbers and idempotency UUIDs. There are no `{{base_url}}` placeholders or environment lookups. Runtime scripts update detail URLs to newly created IDs and account fields to authenticated profiles. JWTs are acquired at Send time instead of embedding expiring tokens.

## Repeat safely

Each literal `idempotencyKey` protects repeated Send with unchanged details by replaying the same operation. For an intentional new operation, first confirm the previous outcome in history, then replace that body's key with a new UUID. Never change a key to retry an uncertain response. Changing an amount or recipient while retaining a used key should be rejected.

Run the create folder in order for an entirely new journey, using new keys for intended new operations. Payment details may show REFUNDED after the refund request; money requests may show PAID after approval. These are persisted current records. All funds are simulated INR.

## Run from CMD

No environment argument is needed:

```cmd
node --use-system-ca artifacts\postman-tools\node_modules\newman\bin\newman.js run postman\Live-Data.postman_collection.json --timeout-request 180000
```

`--use-system-ca` was verified with installed Node 24.3.0 and the managed Windows certificate store. TLS certificate/hostname verification remains enabled.

Real curl seed/verification:

```cmd
artifacts\native\python\python.exe scripts\e2e\postman-live-curl.py
```

This invokes Windows curl, logs in, adds INR 20 of test funds, creates payment/refund/transfer/request records, retrieves them, verifies balance changes, and checks private-service denial. Each deliberate invocation creates a new sample journey. JWTs stay in memory/stdin, outside command lines and evidence. Curl uses best-effort revocation checking on this managed network because revocation endpoints were unreachable; certificate-chain and hostname verification remain enabled.

`scripts/build-live-postman.py` rebuilds the collection using the curl seed IDs. Do not regenerate to retry a request: generation intentionally creates new example usernames and operation keys.

After Send, **Visualize** shows the accepted trace and Cloud Logging/Cloud Trace links. Opening these requires Google Cloud viewing permissions. Tokens are never put into investigation URLs or committed variables.
