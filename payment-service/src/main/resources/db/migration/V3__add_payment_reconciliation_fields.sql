ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS bank_transaction_id UUID,
    ADD COLUMN IF NOT EXISTS reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reconciliation_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payment_bank_transaction ON payments(bank_transaction_id);
