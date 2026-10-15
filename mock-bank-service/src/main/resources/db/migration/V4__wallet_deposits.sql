CREATE TABLE wallet_deposit (
    id UUID PRIMARY KEY,
    account_number VARCHAR(64) NOT NULL REFERENCES bank_account(account_number),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 100000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wallet_deposit_account ON wallet_deposit(account_number, created_at);
