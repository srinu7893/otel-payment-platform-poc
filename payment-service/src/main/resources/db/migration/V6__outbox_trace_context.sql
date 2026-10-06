-- Nullable columns preserve compatibility with already-staged and pre-agent events.
ALTER TABLE outbox_event ADD COLUMN traceparent VARCHAR(55);
ALTER TABLE outbox_event ADD COLUMN tracestate VARCHAR(512);
