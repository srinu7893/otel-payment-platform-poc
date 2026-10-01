CREATE TABLE IF NOT EXISTS refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL,
    bank_transaction_id UUID,
    failure_code VARCHAR(80),
    reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    last_reconciliation_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_refund_payment UNIQUE (payment_id),
    CONSTRAINT uk_refund_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX IF NOT EXISTS idx_refund_customer ON refunds(customer_id);
CREATE INDEX IF NOT EXISTS idx_refund_status ON refunds(status);
CREATE INDEX IF NOT EXISTS idx_refund_created_at ON refunds(created_at);
