CREATE TABLE IF NOT EXISTS debit_transactions (
    transaction_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL,
    message VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_debit_transaction_payment UNIQUE (payment_id)
);

CREATE INDEX IF NOT EXISTS idx_debit_transaction_account ON debit_transactions(account_number);
CREATE INDEX IF NOT EXISTS idx_debit_transaction_status ON debit_transactions(status);
CREATE INDEX IF NOT EXISTS idx_debit_transaction_created_at ON debit_transactions(created_at);
