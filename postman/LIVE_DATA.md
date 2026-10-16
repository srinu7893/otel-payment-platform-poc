# Send requests against existing sandbox data

After Send, open **Visualize** for the request's Cloud Trace and correlated-log links. `last_trace_id` and `last_correlation_id` are saved automatically. New cloud assertions verify the active trace header; viewing stored signals still requires Google Cloud read access. See [OTEL_CLOUD_DEMO.md](../docs/OTEL_CLOUD_DEMO.md).

Verified against the deployed app on 2026-10-16: **28 collection requests, 32 assertions, zero failures**. Each request also performs automatic login.

Import **Live-Data.postman_collection.json** and **Live-Data.postman_environment.json**, then select **Payment Platform - Live Data (deployed sandbox)**. Open **01 - Fetch existing data immediately** and click **Send** on any request. You do not need to register, initialize users, or manually copy a token. Each request logs into the appropriate existing demo account automatically.

These are actual persisted records in the deployed mock bank/payment database, created for this collection on 2026-10-16. They are simulated INR transactions, not external bank transactions:

| Account / action | Seeded data |
| --- | --- |
| Sender | demo / demo123, ACC1001 |
| Receiver | receiver / receiver123, ACC2001 |
| Deposit | INR 100 added to ACC1001 |
| Merchant payment | INR 12.50, Postman live grocery purchase |
| Refunded purchase | INR 5, Postman live refund purchase, fully refunded |
| Transfer | INR 7.50 from ACC1001 to ACC2001 |
| Money request | INR 3 requested from ACC2001 for a dinner share |

The actual deposit/payment/transfer/request IDs are already in the environment. Balances and statuses can change as you use these shared demo accounts. There is no promise that the seeded money request stays OPEN after someone approves it. History may include earlier sandbox tests too.

## Create something, then fetch it

Open **02 - Create and retrieve new transactions**. You can add INR 10, create an INR 2 merchant payment, transfer INR 1, or request INR 1 from the receiver. After a successful create, its response script updates `live_payment_id`, `live_transfer_id`, or `live_request_id` in the selected environment. The matching **Fetch ... just created** request uses the saved ID automatically. The final fetch requests show updated balance, ledger, payment history, transfers and requests.

**Receiver: pay latest money request** automatically signs in as receiver; sender requests use demo. **Refund latest merchant payment** returns the full payment amount once. In the collection runner, use one iteration and keep both folders in order. For standalone requests, choose the operation you want and click Send; mutation requests intentionally change this shared sandbox data.

## Repeating requests safely

Each create operation retains its own `live_new_*_key`. Repeated Send with unchanged details replays that operation rather than moving funds twice. To intentionally create another transaction, first verify the previous operation has finished, then clear only its corresponding environment key:

| New operation | Environment key to clear |
| --- | --- |
| Deposit | live_new_deposit_key |
| Payment | live_new_payment_key |
| Transfer | live_new_transfer_key |
| Money request | live_new_request_key |
| Refund | live_new_refund_key |

Do not clear a key to retry a lost or pending response. Keep the key and inspect history/details. If status is PROCESSING or RECONCILIATION_REQUIRED, wait and fetch details again. If login fails while Cloud Run starts, retry the request; the create call is skipped when automatic login fails. Do not change the payload while retaining an already-used key.

If running through Collection Runner, enable its option to retain variable values when you want to reuse the updated IDs afterward. Newman does not write tokens into the committed files. Clear runtime tokens/personal credentials before exporting or sharing. The provided credentials are the existing public sandbox demo credentials.

The original **Payment-Platform.postman_collection.json** remains the separate isolated acceptance suite with fresh registrations and exact-balance assertions. Use this Live Data collection for immediate exploration with existing data.
