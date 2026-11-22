-- Phase 5 (P14): de-duplicated alerts.
-- detection-service publishes one RuleHitsDetectedEvent per account per transaction. Without
-- de-dup, 10 structuring deposits in one day would open 10 alerts for the same pattern.
-- One alert per (account, rule, time window); every hit is kept as evidence in alert_hit.

CREATE TABLE alert (
    id                 VARCHAR(36)   PRIMARY KEY,
    -- accountId:ruleCode:windowBucket - the UNIQUE constraint is what makes de-dup race-free
    dedup_key          VARCHAR(255)  NOT NULL,
    account_id         VARCHAR(50)   NOT NULL,
    rule_code          VARCHAR(50)   NOT NULL,
    window_bucket      VARCHAR(64)   NOT NULL,
    status             VARCHAR(30)   NOT NULL,          -- NEW | UNDER_REVIEW | ESCALATED | CLOSED_*
    risk_score         DECIMAL(5,2)  NOT NULL,          -- highest score seen across all hits
    latest_explanation TEXT          NOT NULL,          -- explanation of the most recent transaction
    hit_count          INT           NOT NULL DEFAULT 0,
    first_txn_at       DATETIME(6)   NOT NULL,
    last_txn_at        DATETIME(6)   NOT NULL,
    first_detected_at  DATETIME(6)   NOT NULL,
    last_detected_at   DATETIME(6)   NOT NULL,
    status_note        TEXT          NULL,
    status_changed_at  DATETIME(6)   NULL,
    version            INT           NOT NULL DEFAULT 0, -- optimistic lock for analyst updates
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT uk_alert_dedup UNIQUE (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_alert_status_score ON alert (status, risk_score);
CREATE INDEX idx_alert_account      ON alert (account_id);
CREATE INDEX idx_alert_last_detect  ON alert (last_detected_at);

CREATE TABLE alert_hit (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    alert_id         VARCHAR(36)   NOT NULL,
    event_id         VARCHAR(36)   NOT NULL,            -- RuleHitsDetectedEvent.eventId
    rule_code        VARCHAR(50)   NOT NULL,
    transaction_id   VARCHAR(36)   NOT NULL,
    risk_score       DECIMAL(5,2)  NOT NULL,            -- account score for that transaction
    weight           DECIMAL(5,2)  NOT NULL,
    explanation      TEXT          NOT NULL,
    evidence_txn_ids JSON          NOT NULL,
    txn_timestamp    DATETIME(6)   NOT NULL,
    detected_at      DATETIME(6)   NOT NULL,
    received_at      DATETIME(6)   NOT NULL,
    -- at-least-once delivery: a redelivered event must not be attached twice
    CONSTRAINT uk_alert_hit_event_rule UNIQUE (event_id, rule_code),
    CONSTRAINT fk_alert_hit_alert FOREIGN KEY (alert_id) REFERENCES alert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_alert_hit_alert ON alert_hit (alert_id, txn_timestamp);
