CREATE TABLE aml_alert (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    rule_triggered VARCHAR(50) NOT NULL,
    alert_amount DECIMAL(19, 2) NOT NULL,
    risk_score DECIMAL(5, 2) NOT NULL,
    explanation TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_rule FOREIGN KEY (rule_triggered) REFERENCES aml_rule(rule_code)
);

CREATE INDEX idx_aml_alert_account ON aml_alert(account_id);
CREATE INDEX idx_aml_alert_status ON aml_alert(status);