# Support enhancements: demonstrate detection, action and recovery

## What this increment adds

- Durable payment counts by current status, pending outbox count/oldest age, notification status counts/oldest unsent age and mean received-to-sent delay. Scheduled SQL reads refresh cached gauges every 10 seconds; export callbacks do not query the database. A failed refresh preserves the last values and marks the snapshot unhealthy. Check freshness before using counts. These gauges measure current database state, not event rate or a business availability percentage. Payment counts cover payment.payments; transfer/refund tables are not included in these new gauges.
- Prometheus sends its warnings to Alertmanager. Alertmanager groups/routes warnings to a local SQLite-backed incident inbox and sends resolved notifications. Every rule has the PoC support owner and a runbook link. The inbox supports acknowledgement, deduplicates deliveries by fingerprint/start, retains acknowledgement across resolution/restart and caps retained incidents. It is a local demonstration, not a secure production incident/paging service or immutable audit log.
- An on-demand synthetic journey signs in, checks invalid credentials, submits a one-unit fake payment, replays the same idempotency key and waits for notification. It exports success/duration metrics over OTLP and records safe stage timings and IDs. This is a manual check, not continuous monitoring; stale/expired probe data is not proof of current health.
- Six new business dashboard panels show durable state, delivery age, snapshot freshness and the on-demand probe. Queries use max across replicas sharing the same database, not sum, to avoid counting the same rows repeatedly.
- Separate manual acceptance validates gauges against PostgreSQL, stops RabbitMQ, creates a real fake payment, waits for the delayed-outbox warning to reach the inbox, acknowledges it, restores RabbitMQ and verifies delivery plus resolution of the same incident. The broker is restored in a finally block. Ordinary CI only runs unit/build checks; it does not start this failure drill.

## Start and look

Run bash scripts/run-poc.sh start from the repository on a machine with the prerequisites in the main guide. The extra services start through the OTel Compose overlay.

- Support UI: http://localhost:3000 ; isolated local support/support123 login. The observability card links to the inbox and Alertmanager only in the local build.
- Incident inbox: http://localhost:9094 ; delivery, owner, acknowledgement and resolution.
- Alertmanager: http://localhost:9093 ; active alerts, grouping and silences. A silence suppresses notifications, it does not repair the service. Set an expiry and reason and verify the incident separately.
- Business dashboard: http://localhost:3001/d/payment-business ; current rows and waiting age alongside log-event counts.
- Prometheus alerts: http://localhost:9090/alerts ; evaluate the rule before blaming notification routing.

The inbox/Alertmanager host ports bind to loopback. Do not publish them to the internet. No email/Slack/pager integration or outbound human notification is configured. Real routing, escalation, access control and credentials must be selected and tested separately.

## Manual commands

1. Run python3 scripts/e2e/synthetic-journey.py on the isolated local demo. Inspect artifacts/synthetic-journey.json. Each run creates one fake payment using a new idempotency key; replay uses the same key. It does not hit a real bank.
2. Run python3 scripts/e2e/support-acceptance.py on a fresh disposable stack. This stops/restores the demo broker and can take several minutes. It requires the synthetic probe to have run and the existing local fixture accounts. Inspect artifacts/support-results.json and artifacts/incident-inbox.json.
3. For the complete suite, use the opt-in manual workflow described in the main guide. Its artifact includes these results, backend reports, SQL summaries and browser screenshots. Only passing results establish live behavior.

## Queries and how to interpret them

| Prometheus query | Meaning and action |
|---|---|
| max by (status) (poc_payment_records) | Durable payment rows by current status. A decline is not automatically an infrastructure fault. Reconciliation-required needs bank outcome investigation. |
| max(poc_outbox_pending) | Saved work waiting for publication. Payment may already be committed; do not repeat it. |
| max(poc_outbox_oldest_pending_seconds) | Age of the oldest saved pending event. Over 60 seconds for 30 seconds triggers the PoC warning. Inspect broker, relay failures and retries. |
| max by (status) (poc_notification_records) | Delivery records by current status. There can be multiple channel records for one source event; not unique payments. |
| max(poc_notification_oldest_unsent_seconds) | Includes failed records until resolved; alerts support to investigate. |
| max(poc_notification_mean_received_to_sent_seconds) | Average consumer record creation-to-SENT over stored sent rows. Excludes the time before the consumer created the record and is not a tail-latency SLI. No sent rows means unknown, not zero delay. |
| max by (service_name) (time() - poc_business_snapshot_last_success_timestamp_seconds) | Snapshot age. A stale snapshot cannot prove current health. |
| poc_business_snapshot_healthy | 1 means last refresh succeeded; 0 means failed/not yet refreshed. Also check timestamps and missing series. |
| poc_synthetic_journey_success | Last exported on-demand journey result: 1 pass, 0 fail. Check run time/evidence. |
| ALERTS{alertstate="firing"} | Current active Prometheus warnings; compare with inbox delivery. |

## Practical triage and RCA

Payment completed/no notification: locate the payment log and trace, check saved outbox state and age, inspect RabbitMQ and consumer count, then restore the failing dependency. Verify saved work catches up and the same incident resolves. Never issue another payment to repair a notification.

Bank exception/timeout: inspect the bank span and business outcome; confirm whether the operation happened before any retry. Preserve the trace, payment ID, source revision, symptom/action/recovery times and confirmed cause. Keep secrets and customer data out of metrics and shared incident reports.

Telemetry missing: check snapshot freshness, Collector accepted/refused/exported signals, queues, scrape targets and backend availability. A receiver accepting a payload does not prove downstream storage. An acknowledged alert does not prove recovery; use a fresh successful transaction and settled durable state.

Measure time to detect/identify/restore during repeated drills before claiming improved MTTR. The inbox timestamps are local receipt/ack times, not a complete incident measurement system. Safe RCA still requires dependency/network/database evidence to confirm the underlying cause.

## Production boundaries and next features

Queries scan current PoC tables. For large production tables, add indexes/materialized summaries or incremental durable aggregates, DB statement timeouts, suitable refresh intervals and capacity tests. Payment/notification gauges are separate snapshots, not one consistent cross-service transaction. Metrics labels contain fixed statuses, not payment/customer/account IDs. SQL spans from monitoring queries can add overhead and should be budgeted/filtered if necessary.

The new counts do not establish a windowed final-payment success SLO. Next implement durable outcome/delivery-time SLIs, stronger structured privacy coverage, browser instrumentation with safe ingestion, selected domain spans/span links, database pool/saturation monitoring, secure paging/SSO, profiling if needed, trace-aware multi-replica sampling and HA/DR/load tests. See CLOUD_RUN_PLAN.md for the feature catalogue and platform-specific prerequisites.

GCP status: manifests/manual release/acceptance assets are prepared; this session has no concrete cloud settings or credentials. The available example still contains placeholders. Deploy seven Java services plus frontend separately, with a Collector per Java service; provision durable PostgreSQL/RabbitMQ first. Set gcp-poc WIF/deployer/environment config and Secret Manager/IAM references, then validate/deploy/run manual acceptance. Local Alertmanager/inbox are not included in the Cloud Run manifests. Select a production alerting endpoint before claiming cloud alert delivery. BigQuery linking and Grafana Cloud ingestion remain unexecuted account-specific steps.
