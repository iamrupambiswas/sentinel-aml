-- Phase 7b: investigation cases and the tamper-evident audit log.

-- ---------------------------------------------------------------- audit log
-- Append-only, hash-chained: hash = SHA-256(seq | event fields | prev_hash).
-- Changing or removing any row breaks every hash after it, which GET /api/v1/audit/verify detects.
CREATE TABLE audit_event (
    seq          BIGINT        PRIMARY KEY,          -- 1, 2, 3 ... assigned under the chain-head lock, no gaps
    event_id     VARCHAR(36)   NOT NULL,             -- the sender's id; a redelivered event is stored once
    occurred_at  DATETIME(6)   NOT NULL,             -- when the action was committed (UTC)
    recorded_at  DATETIME(6)   NOT NULL,             -- when it was appended here (UTC)
    service      VARCHAR(50)   NOT NULL,
    actor        VARCHAR(100)  NOT NULL,             -- Keycloak username or "system"
    action       VARCHAR(50)   NOT NULL,
    entity_type  VARCHAR(30)   NOT NULL,
    entity_id    VARCHAR(100)  NOT NULL,
    details      TEXT          NOT NULL,             -- canonical JSON (sorted keys); TEXT, not JSON, so the hashed bytes are stored exactly
    prev_hash    CHAR(64)      NOT NULL,
    hash         CHAR(64)      NOT NULL,
    CONSTRAINT uk_audit_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id, seq);
CREATE INDEX idx_audit_actor  ON audit_event (actor, seq);

-- The database itself refuses to rewrite history, even for the application's own DB user.
-- (Dropping the trigger needs DDL rights; the hash chain still exposes any change made that way.)
CREATE TRIGGER trg_audit_event_no_update BEFORE UPDATE ON audit_event
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only: UPDATE is not allowed';

CREATE TRIGGER trg_audit_event_no_delete BEFORE DELETE ON audit_event
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only: DELETE is not allowed';

-- One row: the end of the chain. Appenders lock it (SELECT ... FOR UPDATE), so appends are strictly ordered
-- even with several pods and consumer threads, and the lock is held until commit.
CREATE TABLE audit_chain_head (
    id         TINYINT      PRIMARY KEY,
    last_seq   BIGINT       NOT NULL,
    last_hash  CHAR(64)     NOT NULL
) ENGINE=InnoDB;

INSERT INTO audit_chain_head (id, last_seq, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

-- ---------------------------------------------------------------- cases
-- A case groups the alerts an analyst investigates together (e.g. every alert on one customer's accounts).
CREATE TABLE aml_case (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,   -- shown as CASE-000042
    title        VARCHAR(200)  NOT NULL,
    status       VARCHAR(30)   NOT NULL,                     -- OPEN | INVESTIGATING | CLOSED_NO_ACTION | CLOSED_REPORTED
    priority     VARCHAR(10)   NOT NULL,                     -- LOW | MEDIUM | HIGH
    assignee     VARCHAR(100)  NULL,
    created_by   VARCHAR(100)  NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    closed_at    DATETIME(6)   NULL,
    resolution   TEXT          NULL,                         -- required when closing
    version      INT           NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_case_status ON aml_case (status, priority, updated_at);
CREATE INDEX idx_case_assignee ON aml_case (assignee, status);

CREATE TABLE case_alert (
    case_id   BIGINT        NOT NULL,
    alert_id  VARCHAR(36)   NOT NULL,
    added_by  VARCHAR(100)  NOT NULL,
    added_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (case_id, alert_id),
    CONSTRAINT uk_case_alert_alert UNIQUE (alert_id),          -- an alert belongs to at most one case
    CONSTRAINT fk_case_alert_case FOREIGN KEY (case_id) REFERENCES aml_case (id),
    CONSTRAINT fk_case_alert_alert FOREIGN KEY (alert_id) REFERENCES alert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE case_note (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    case_id     BIGINT        NOT NULL,
    author      VARCHAR(100)  NOT NULL,
    body        TEXT          NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT fk_case_note_case FOREIGN KEY (case_id) REFERENCES aml_case (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_case_note_case ON case_note (case_id, created_at);
