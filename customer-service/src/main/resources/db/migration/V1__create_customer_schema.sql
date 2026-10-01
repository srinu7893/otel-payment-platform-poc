CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    account_number VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_customer_account ON customers(account_number);
CREATE INDEX IF NOT EXISTS idx_customer_active ON customers(active);
