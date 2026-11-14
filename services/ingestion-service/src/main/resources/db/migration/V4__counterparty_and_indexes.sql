-- Counterparty data is needed by the HIGH_RISK_JURISDICTION rule (P9).
ALTER TABLE transaction
    ADD COLUMN counterparty_name    VARCHAR(255) NULL,
    ADD COLUMN counterparty_country VARCHAR(3)   NULL,
    ADD COLUMN batch_id             VARCHAR(36)  NULL,
    ADD COLUMN ingested_at          DATETIME(6)  NULL;

-- Composite indexes recommended in the blueprint (account + time range scans)
CREATE INDEX idx_txn_source_time ON transaction (source_account_id, txn_timestamp);
CREATE INDEX idx_txn_dest_time   ON transaction (destination_account_id, txn_timestamp);

-- The outbox poller scans PENDING rows oldest-first on every tick
CREATE INDEX idx_outbox_status_created ON outbox_event (status, created_at);
ALTER TABLE outbox_event ADD COLUMN published_at DATETIME(6) NULL;

CREATE INDEX idx_ingestion_error_batch ON ingestion_error (batch_id);
