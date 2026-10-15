CREATE TABLE money_request (
    id UUID PRIMARY KEY,
    requester_customer_id VARCHAR(80) NOT NULL,
    requester_account VARCHAR(64) NOT NULL,
    requester_name VARCHAR(160) NOT NULL,
    payer_account VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 100000),
    note VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    transfer_id UUID
);
CREATE INDEX idx_money_request_requester ON money_request(requester_customer_id,created_at);
CREATE INDEX idx_money_request_payer ON money_request(payer_account,created_at);
