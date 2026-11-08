-- V1__init.sql: Initial schema for Customer Service

CREATE TABLE customer (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(20) NOT NULL, -- RETAIL or BUSINESS
    full_name VARCHAR(100) NOT NULL,
    id_number VARCHAR(255) NOT NULL, -- Stored encrypted
    dob DATE,
    nationality VARCHAR(3) NOT NULL, -- ISO Alpha-3
    risk_rating VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH
    onboarded_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL, -- CHECKING, SAVINGS, CORPORATE
    currency VARCHAR(3) NOT NULL, -- ISO Currency Code
    opened_at DATETIME(6) NOT NULL,
    risk_rating VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL, -- ACTIVE, SUSPENDED, CLOSED
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for frequent lookups
CREATE INDEX idx_customer_rating ON customer(risk_rating);
CREATE INDEX idx_account_customer_id ON account(customer_id);