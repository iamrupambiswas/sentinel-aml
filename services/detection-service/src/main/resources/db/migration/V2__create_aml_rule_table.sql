CREATE TABLE aml_rule (
    rule_code VARCHAR(50) PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    description TEXT,
    threshold_amount DECIMAL(19, 2) NOT NULL,
    window_minutes BIGINT NOT NULL,
    weight DECIMAL(5, 2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert our initial velocity rule dynamically
INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('24H_VELOCITY_LIMIT', '24-Hour Velocity Check', 'Flags accounts exceeding $10,000 in a 24-hour sliding window', 10000.00, 1440, 30.00, TRUE);

-- Insert the Structuring rule
INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('STRUCTURING', 'Structuring / Smurfing', 'Flags accounts with 3 or more transactions between $9,000 and $9,999 within 24 hours', 3.00, 1440, 70.00, TRUE);

INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('RAPID_MOVEMENT', 'Rapid Movement / Layering', 'Flags accounts moving >= 80% of deposited funds out within 48 hours', 80.00, 2880, 75.00, TRUE);

INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('HIGH_RISK_JURISDICTION', 'High-Risk Jurisdiction / Sanctions', 'Flags transactions involving watchlisted countries or counterparties', 0.00, 0, 90.00, TRUE);

INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('BEHAVIORAL_DEVIATION', 'Behavioral Deviation', 'Flags daily volume exceeding 3x the 90-day average', 3.00, 1440, 55.00, TRUE);

INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active)
VALUES ('ROUND_AMOUNT', 'Repeated Round Numbers', 'Flags 5 or more round-number transfers in a 7-day window', 5.00, 10080, 40.00, TRUE);