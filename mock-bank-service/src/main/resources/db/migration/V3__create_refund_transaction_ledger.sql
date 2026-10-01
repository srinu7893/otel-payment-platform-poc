CREATE TABLE IF NOT EXISTS refund_transactions (
    transaction_id UUID PRIMARY KEY,
    refund_id UUID NOT NULL,
    original_payment_id UUID NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL,
    message VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refund_transaction_refund UNIQUE (refund_id)
);

CREATE INDEX IF NOT EXISTS idx_refund_transaction_payment ON refund_transactions(original_payment_id);
CREATE INDEX IF NOT EXISTS idx_refund_transaction_account ON refund_transactions(account_number);
CREATE INDEX IF NOT EXISTS idx_refund_transaction_created_at ON refund_transactions(created_at);
