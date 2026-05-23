CREATE TABLE transaction_status (
    id SERIAL PRIMARY KEY,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason TEXT
);

CREATE TABLE ledger_status (
    id SERIAL PRIMARY KEY,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    account_debited BOOLEAN NOT NULL DEFAULT FALSE,
    beneficiary_credited BOOLEAN NOT NULL DEFAULT FALSE
);
