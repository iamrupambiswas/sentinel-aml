CREATE TABLE transaction (
    id VARCHAR(36) PRIMARY KEY,
    external_ref VARCHAR(255) UNIQUE,
    source_account_id VARCHAR(50) NOT NULL,
    destination_account_id VARCHAR(50) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_inr DECIMAL(19,4),
    type VARCHAR(20) NOT NULL,
    txn_timestamp DATETIME(6) NOT NULL
);

CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL
);