CREATE TABLE processed_event (
    transaction_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

-- Index for fast idempotency lookups
CREATE INDEX idx_processed_event_time ON processed_event(processed_at);