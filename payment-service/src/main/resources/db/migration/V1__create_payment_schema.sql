CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    merchant VARCHAR(120) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payment_customer ON payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payment_created_at ON payments(created_at);

CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    sender_account VARCHAR(64) NOT NULL,
    receiver_account VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    bank_transaction_id UUID,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfer_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_transfer_distinct_accounts CHECK (sender_account <> receiver_account)
);

CREATE INDEX IF NOT EXISTS idx_transfer_customer ON transfers(customer_id);
CREATE INDEX IF NOT EXISTS idx_transfer_sender ON transfers(sender_account);
CREATE INDEX IF NOT EXISTS idx_transfer_receiver ON transfers(receiver_account);
CREATE INDEX IF NOT EXISTS idx_transfer_status ON transfers(status);
CREATE INDEX IF NOT EXISTS idx_transfer_created_at ON transfers(created_at);
