CREATE TABLE IF NOT EXISTS user_account (
    username VARCHAR(80) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_customer ON user_account(customer_id);

CREATE TABLE IF NOT EXISTS user_role (
    username VARCHAR(80) NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT fk_user_role_user FOREIGN KEY (username) REFERENCES user_account(username) ON DELETE CASCADE,
    CONSTRAINT uk_user_role UNIQUE (username, role)
);

CREATE INDEX IF NOT EXISTS idx_user_role_username ON user_role(username);
