-- Linked Cloud Logging dataset schema, not the schema of a direct export sink.
-- Replace PROJECT_ID and LINKED_DATASET, inspect your view schema and set query budgets.
-- 1. Raw application logs in a bounded period. Keep structured fields for investigation.
SELECT timestamp, severity, log_name, resource, trace, span_id, insert_id,
       text_payload, json_payload
FROM `PROJECT_ID.LINKED_DATASET._AllLogs`
WHERE timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
  AND log_name = 'projects/PROJECT_ID/logs/payment-poc-otel'
ORDER BY timestamp DESC
LIMIT 1000;

-- 2. Ordered raw events for one execution across services. Replace TRACE_ID.
SELECT timestamp, severity, resource, span_id,
       COALESCE(text_payload, JSON_VALUE(json_payload, '$.message'), TO_JSON_STRING(json_payload)) AS message
FROM `PROJECT_ID.LINKED_DATASET._AllLogs`
WHERE timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
  AND log_name = 'projects/PROJECT_ID/logs/payment-poc-otel'
  AND trace = 'projects/PROJECT_ID/traces/TRACE_ID'
ORDER BY timestamp ASC
LIMIT 1000;

-- 3. Hourly emitted business events. These are log event counts, not unique transactions.
WITH events AS (
  SELECT timestamp,
         REGEXP_EXTRACT(COALESCE(text_payload, JSON_VALUE(json_payload, '$.message'),
                                TO_JSON_STRING(json_payload)), r'event=([A-Z_]+)') AS event
  FROM `PROJECT_ID.LINKED_DATASET._AllLogs`
  WHERE timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
    AND log_name = 'projects/PROJECT_ID/logs/payment-poc-otel'
)
SELECT TIMESTAMP_TRUNC(timestamp, HOUR) AS hour, event, COUNT(*) AS emitted_events
FROM events
WHERE event IS NOT NULL
GROUP BY hour, event
ORDER BY hour DESC, emitted_events DESC
LIMIT 1000;
