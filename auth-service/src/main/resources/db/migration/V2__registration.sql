CREATE TABLE registration (
    username VARCHAR(80) PRIMARY KEY REFERENCES user_account(username),
    account_number VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);
