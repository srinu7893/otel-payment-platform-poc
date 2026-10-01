CREATE TABLE IF NOT EXISTS bank_account (
    account_number VARCHAR(64) PRIMARY KEY,
    customer_name VARCHAR(160) NOT NULL,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bank_account_active ON bank_account(active);

CREATE TABLE IF NOT EXISTS bank_transaction (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    type VARCHAR(40) NOT NULL,
    sender_account VARCHAR(64) NOT NULL,
    receiver_account VARCHAR(64),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bank_tx_payment ON bank_transaction(payment_id);
CREATE INDEX IF NOT EXISTS idx_bank_tx_sender ON bank_transaction(sender_account);
CREATE INDEX IF NOT EXISTS idx_bank_tx_receiver ON bank_transaction(receiver_account);
CREATE INDEX IF NOT EXISTS idx_bank_tx_created ON bank_transaction(created_at);
