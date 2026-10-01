ALTER TABLE transfers
    ADD COLUMN IF NOT EXISTS reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reconciliation_at TIMESTAMPTZ;
