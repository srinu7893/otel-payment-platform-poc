CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    payment_id UUID,
    transfer_id UUID,
    customer_id VARCHAR(80),
    event_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    destination VARCHAR(160),
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notification_payment ON notifications(payment_id);
CREATE INDEX IF NOT EXISTS idx_notification_transfer ON notifications(transfer_id);
CREATE INDEX IF NOT EXISTS idx_notification_customer ON notifications(customer_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON notifications(status);
