# Payment sandbox guide

Open https://payment-frontend-52916499838.us-central1.run.app/ and sign in.

| User | Password | Account | Purpose |
| --- | --- | --- | --- |
| demo | demo123 | ACC1001 | Sender and merchant payments |
| receiver | receiver123 | ACC2001 | Receiver, or payer of a money request |
| support | support123 | — | Service health and transaction lookup |
| admin | admin123 | — | Operations dashboard |

All funds are simulated INR balances in the mock bank. No real bank, card, UPI, or deposit provider is connected. There is no public account signup. An arbitrary new external bank account cannot receive a real transfer through this sandbox.

## Try the complete customer journey

1. Sign in as **demo**. **My account** shows your linked account and available bank balance.
2. Open **Add demo money**, enter `500`, choose **Review deposit**, then **Confirm transaction**. The balance increases by INR 500. This is a simulated deposit; it never charges a funding source.
3. Open **Pay merchant**, enter `Demo Store` and `25`, review and confirm. A completed payment debits INR 25.
4. Open **History**, then **Details** for that payment. You can see the full transaction ID, creation time, amount, status, and bank transaction reference. Choose **Refund payment** to return the full amount once.
5. Open **Send money**. Enter `ACC2001` and `10`. Review the recipient name before confirming. Your account decreases by INR 10 and the receiver increases by INR 10.
6. Sign out and sign in as **receiver**. **My account → Account activity** shows the incoming credit. Transfers appear in the sender's outgoing transfer history and both accounts' bank activity.
7. Open **Request money**, enter `ACC1001`, amount `5`, and an optional note. Review and confirm. This sends a request and does not move funds.
8. Sign in as **demo**, open **Request money**, find the received request and choose **Pay request**. Confirm the payer prompt. The request becomes **PAID** after the transfer completes. Choosing **Decline** never moves funds; the requester can cancel an open request.
9. Open **Notifications** and refresh after a transaction. Payment, transfer, and refund events arrive asynchronously through the existing outbox/RabbitMQ pipeline. Deposits and money-request creation are visible in their wallet/request views, rather than RabbitMQ notifications.

## Send to another account

Use **Send money** and enter another **existing active mock-bank account number**. Recipient lookup returns its name and activity status, without exposing its balance. Unknown and inactive accounts are rejected. For the provided demo users, use ACC1001 and ACC2001. Creating additional mock-bank/customer/login accounts requires administrative provisioning; entering a new number does not create an account or fund it.

## Understand statuses and retries

| Status | Meaning |
| --- | --- |
| COMPLETED | The bank confirmed the movement. |
| FAILED / DECLINED | The transaction did not complete. Read its details. |
| PROCESSING / RECONCILIATION_REQUIRED | The final outcome is not confirmed. Refresh history; do not create another transaction. |
| OPEN | A money request awaits approval. No money has moved. |
| PAID | The money request's transfer completed. |
| CANCELLED / DECLINED on a request | The request cannot be paid and did not move funds. |

The form retains its idempotency key on an HTTP failure. Retrying unchanged details in the same session reuses that key. A page reload or new session does not retain that pending form key: check history before submitting again. Request-payment retries always use a server-derived transfer key. Deposits are atomic with their bank ledger record and repeated keys do not credit twice.

Amounts use at most two decimal places. Wallet deposits and money requests are limited to INR 100,000 per operation. Merchant/transfer risk policies and available funds can impose lower limits. The sandbox supports INR and full merchant refunds only. Transfer reversal, real external deposits, multi-currency settlement, KYC, account signup, and production fraud controls are outside this implementation.

## Verification

Backend journey (intentionally adds INR 10 in demo funds and performs small test transactions):

```cmd
artifacts\native\python\python.exe scripts\e2e\wallet-journey.py --url https://payment-frontend-52916499838.us-central1.run.app/
```

The test checks balances, deposit/refund/transfer idempotency, ownership, recipient lookup, request approval/decline/cancel, history, and asynchronous notifications. It never prints returned access tokens. Results: `artifacts/wallet-journey.json`.

Browser journey using an installed Microsoft Edge:

```cmd
npm install --prefix artifacts/browser-tests --no-save playwright
node scripts\e2e\wallet-browser.cjs https://payment-frontend-52916499838.us-central1.run.app/
```

Browser evidence: `artifacts/wallet-browser.json` and `artifacts/screenshots/wallet-*.png`. This adds INR 2 in demo funds and performs small payments/transfers/requests.

Deploy changed services manually after passing exact-commit CI: **mock-bank-service → gateway-service → payment-service → api-gateway → frontend**. Bank migration V4 and payment migration V7 are additive. Existing auth, customer, notification services and IAM access stay in place. Each workflow still builds only its selected service, uses WIF and immutable digests, checks the tagged candidate, verifies traffic promotion, and retains rollback controls.
