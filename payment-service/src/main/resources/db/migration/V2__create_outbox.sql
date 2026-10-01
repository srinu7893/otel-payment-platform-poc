CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_event(status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_event(aggregate_type, aggregate_id);
