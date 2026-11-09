CREATE TABLE ingestion_batch (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    total_records INT DEFAULT 0,
    failed_records INT DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6)
);

CREATE TABLE ingestion_error (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    line_number INT,
    raw_data TEXT,
    error_reason TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_batch_error FOREIGN KEY (batch_id) REFERENCES ingestion_batch(id)
);