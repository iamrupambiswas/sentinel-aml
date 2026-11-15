-- Rule parameters live in JSON so thresholds can be tuned without code changes (P13).
-- All money thresholds are in INR because ingestion normalizes every amount to amount_inr (P18).
ALTER TABLE aml_rule
    ADD COLUMN params  JSON NULL,
    ADD COLUMN version INT  NOT NULL DEFAULT 1;

-- Rule 1 was implemented in code but never seeded, so it could never fire.
INSERT INTO aml_rule (rule_code, rule_name, description, threshold_amount, window_minutes, weight, is_active, params)
VALUES ('CTR_THRESHOLD', 'Currency Transaction Report Threshold',
        'Flags any single transaction >= the INR equivalent of USD 10,000', 830000.00, 0, 30.00, TRUE, NULL);

-- Superseded by CTR_THRESHOLD / BEHAVIORAL_DEVIATION; kept (FK history) but disabled.
UPDATE aml_rule SET is_active = FALSE WHERE rule_code = '24H_VELOCITY_LIMIT';

-- Structuring: >= 3 txns in [USD 9,000, 9,999] (= INR 7.515L..8.35L at 83.5) within 24h
UPDATE aml_rule SET threshold_amount = 3, window_minutes = 1440, weight = 70,
       description = 'Flags >= 3 transactions just below the CTR threshold on one account within 24 hours',
       params = JSON_OBJECT('minAmountInr', 751500, 'maxAmountInr', 834999)
 WHERE rule_code = 'STRUCTURING';

UPDATE aml_rule SET threshold_amount = 80, window_minutes = 2880, weight = 75,
       params = JSON_OBJECT('minCreditInr', 100000)
 WHERE rule_code = 'RAPID_MOVEMENT';

UPDATE aml_rule SET weight = 90,
       params = JSON_OBJECT('nameSimilarity', 0.92)
 WHERE rule_code = 'HIGH_RISK_JURISDICTION';

UPDATE aml_rule SET threshold_amount = 3, window_minutes = 1440, weight = 55,
       params = JSON_OBJECT('minDailyInr', 50000, 'baselineDays', 90)
 WHERE rule_code = 'BEHAVIORAL_DEVIATION';

-- Round amount: >= 5 txns in 7 days that are exact multiples of 1,000 (original currency)
-- or within 2% below the CTR threshold, and at least INR 1 lakh.
UPDATE aml_rule SET threshold_amount = 5, window_minutes = 10080, weight = 40,
       params = JSON_OBJECT('modulo', 1000, 'minAmountInr', 100000, 'nearThresholdPct', 2, 'ctrThresholdInr', 830000)
 WHERE rule_code = 'ROUND_AMOUNT';

-- Detection-side record of every rule evaluation that fired (the blueprint's "rule_hit").
ALTER TABLE aml_alert
    ADD COLUMN rule_codes    VARCHAR(255) NULL,
    ADD COLUMN evidence      JSON         NULL,
    ADD COLUMN direction     VARCHAR(10)  NULL,
    ADD COLUMN txn_timestamp DATETIME(6)  NULL;
ALTER TABLE aml_alert MODIFY risk_score DECIMAL(5,2) NOT NULL;
CREATE INDEX idx_aml_alert_txn ON aml_alert (transaction_id);
