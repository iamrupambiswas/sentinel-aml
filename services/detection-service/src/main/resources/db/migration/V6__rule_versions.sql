-- Phase 5 (P13): every rule change is versioned and auditable.
-- aml_rule.version is now a JPA @Version (optimistic lock); this table keeps a full snapshot per
-- version so "what were the thresholds on date X, and who changed them?" has an answer.
CREATE TABLE aml_rule_version (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    rule_code        VARCHAR(50)   NOT NULL,
    version          INT           NOT NULL,
    rule_name        VARCHAR(255)  NOT NULL,
    threshold_amount DECIMAL(19,2) NOT NULL,
    window_minutes   BIGINT        NOT NULL,
    weight           DECIMAL(5,2)  NOT NULL,
    is_active        BOOLEAN       NOT NULL,
    params           JSON          NULL,
    changed_by       VARCHAR(100)  NOT NULL,
    change_reason    VARCHAR(500)  NOT NULL,
    changed_at       DATETIME(6)   NOT NULL,
    CONSTRAINT uk_rule_version UNIQUE (rule_code, version),
    CONSTRAINT fk_rule_version_rule FOREIGN KEY (rule_code) REFERENCES aml_rule (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Baseline: today's state becomes the first recorded version of every rule.
INSERT INTO aml_rule_version (rule_code, version, rule_name, threshold_amount, window_minutes, weight,
                              is_active, params, changed_by, change_reason, changed_at)
SELECT rule_code, version, rule_name, threshold_amount, window_minutes, weight,
       COALESCE(is_active, FALSE), params, 'system', 'Baseline captured by V6 migration', UTC_TIMESTAMP(6)
  FROM aml_rule;
