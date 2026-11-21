package com.sentinel.aml.alert_case_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.event.RuleHitPayload;
import com.sentinel.aml.alert_case_service.entity.AlertStatus;
import com.sentinel.aml.common.event.AlertChangedEvent;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Turns a RuleHitsDetectedEvent into de-duplicated alerts (P14).
 *
 * <h3>Why plain SQL and not "find, then save"</h3>
 * Several consumer threads (and, later, several pods) receive hits for the same account at the
 * same time. "SELECT alert by key; if none, INSERT" lets two threads both see "none" and create
 * two alerts. Here the database decides, atomically:
 * <ol>
 *   <li>{@code INSERT ... ON DUPLICATE KEY UPDATE} on the UNIQUE dedup_key creates the alert or
 *       does nothing. A concurrent inserter of the same key waits on the row lock, then sees the
 *       duplicate - it never fails and never creates a second row.</li>
 *   <li>{@code SELECT ... FOR UPDATE} takes the alert's exclusive row lock up front, so concurrent
 *       hits for the same alert are serialized instead of deadlocking on a lock upgrade.</li>
 *   <li>Under that lock: if (event_id, rule_code) is already in alert_hit, this is a broker
 *       redelivery and nothing else is touched. Otherwise a PLAIN insert attaches the hit. This
 *       check-then-insert is race-free because every copy of a hit maps to the SAME alert (same
 *       account, rule and window), whose lock we hold. UNIQUE (event_id, rule_code) stays as a
 *       safety net.</li>
 *   <li>Counters move with one {@code UPDATE ... hit_count = hit_count + 1, GREATEST(...)},
 *       evaluated under the row lock, so no increment is ever lost.</li>
 * </ol>
 * The datasource uses {@code useAffectedRows=true}, so step 1 returns 1 for "created" and 0 for
 * "already existed".
 *
 * <h3>Why the hit is NOT an upsert</h3>
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} on alert_hit (AUTO_INCREMENT primary key + a second
 * UNIQUE key) takes an exclusive next-key lock on the end of the primary index whenever it finds a
 * duplicate. Two redeliveries doing that at once each held that lock and waited for the other's
 * insert slot - the 50-thread test caught it (64 deadlock retries, some deliveries failing 5 times).
 *
 * <h3>Lock order</h3>
 * Hits are processed in dedup-key order. Two events that touch the same two alerts therefore lock
 * them in the same order and cannot deadlock each other. If MySQL still reports a deadlock, the
 * exception rolls back the JMS session and the broker redelivers; every step is idempotent, so the
 * retry is safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertIngestService {

    static final String INSERT_ALERT = """
            INSERT INTO alert (id, dedup_key, account_id, rule_code, window_bucket, status, risk_score,
                               latest_explanation, hit_count, first_txn_at, last_txn_at,
                               first_detected_at, last_detected_at, version, created_at, updated_at)
            VALUES (:id, :dedupKey, :accountId, :ruleCode, :bucket, 'NEW', 0,
                    :explanation, 0, :txnAt, :txnAt, :detectedAt, :detectedAt, 0, :now, :now)
            ON DUPLICATE KEY UPDATE dedup_key = dedup_key
            """;

    /**
     * Exclusive row lock BEFORE anything else touches the alert. Without it, two transactions for the
     * same alert each took a SHARED lock (the alert_hit foreign-key check) and then both needed an
     * EXCLUSIVE one for APPLY_HIT: a lock-upgrade deadlock that the 50-thread test reproduced.
     * With it, concurrent hits for one alert simply queue.
     */
    static final String LOCK_ALERT = "SELECT id FROM alert WHERE dedup_key = :dedupKey FOR UPDATE";

    static final String HIT_EXISTS =
            "SELECT COUNT(*) FROM alert_hit WHERE event_id = :eventId AND rule_code = :ruleCode";

    static final String INSERT_HIT = """
            INSERT INTO alert_hit (alert_id, event_id, rule_code, transaction_id, risk_score, weight,
                                   explanation, evidence_txn_ids, txn_timestamp, detected_at, received_at)
            VALUES (:alertId, :eventId, :ruleCode, :transactionId, :riskScore, :weight,
                    :explanation, :evidence, :txnAt, :detectedAt, :now)
            """;

    // MySQL evaluates SET left to right: latest_explanation must read last_txn_at BEFORE it moves.
    static final String APPLY_HIT = """
            UPDATE alert
               SET hit_count          = hit_count + 1,
                   risk_score         = GREATEST(risk_score, :riskScore),
                   latest_explanation = IF(:txnAt >= last_txn_at, :explanation, latest_explanation),
                   first_txn_at       = LEAST(first_txn_at, :txnAt),
                   last_txn_at        = GREATEST(last_txn_at, :txnAt),
                   last_detected_at   = GREATEST(last_detected_at, :detectedAt),
                   updated_at         = :now
             WHERE id = :alertId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** What happened to each hit of one event. */
    public record Outcome(int created, int merged, int duplicates) {
        public int total() {
            return created + merged + duplicates;
        }
    }

    @Transactional
    public Outcome ingest(RuleHitsDetectedEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime txnAt = utc(event.txnTimestamp() != null ? event.txnTimestamp() : event.detectedAt());
        LocalDateTime detectedAt = utc(event.detectedAt() != null ? event.detectedAt() : clock.instant());
        BigDecimal riskScore = event.riskScore() != null ? event.riskScore() : BigDecimal.ZERO;

        List<RuleHitPayload> ordered = event.hits().stream()
                .sorted(Comparator.comparing((RuleHitPayload h) -> dedupKey(event, h)))
                .toList();

        int created = 0, merged = 0, duplicates = 0;
        for (RuleHitPayload hit : ordered) {
            String key = dedupKey(event, hit);
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("dedupKey", key)
                    .addValue("accountId", event.accountId())
                    .addValue("ruleCode", hit.ruleCode())
                    .addValue("bucket", bucket(event, hit))
                    .addValue("eventId", event.eventId())
                    .addValue("transactionId", event.transactionId())
                    .addValue("riskScore", riskScore)
                    .addValue("weight", hit.weight() != null ? hit.weight() : BigDecimal.ZERO)
                    .addValue("explanation", hit.explanation() != null ? hit.explanation() : event.explanation())
                    .addValue("evidence", toJson(hit.evidenceTxnIds() != null ? hit.evidenceTxnIds() : List.of()))
                    .addValue("txnAt", txnAt)
                    .addValue("detectedAt", detectedAt)
                    .addValue("now", now);

            boolean isNew = jdbc.update(INSERT_ALERT, p) == 1;
            String alertId = jdbc.queryForObject(LOCK_ALERT, p, String.class);
            p.addValue("alertId", alertId);
            Integer existing = jdbc.queryForObject(HIT_EXISTS, p, Integer.class);
            if (existing != null && existing > 0) {
                // Already applied: redelivery of an event we processed before.
                duplicates++;
                continue;
            }
            jdbc.update(INSERT_HIT, p);
            jdbc.update(APPLY_HIT, p);
            if (isNew) created++;
            else merged++;
            // Raised inside the transaction, sent to the live-alerts topic only AFTER commit (AlertChangePublisher)
            events.publishEvent(new AlertChangedEvent(alertId, event.accountId(), hit.ruleCode(),
                    isNew ? AlertChangedEvent.Change.CREATED : AlertChangedEvent.Change.UPDATED,
                    isNew ? AlertStatus.NEW.name() : null, clock.instant()));
        }

        Outcome outcome = new Outcome(created, merged, duplicates);
        log.info("Event {} (account {}, txn {}): {} new alert(s), {} merged, {} duplicate hit(s)",
                event.eventId(), event.accountId(), event.transactionId(), created, merged, duplicates);
        return outcome;
    }

    /** accountId:ruleCode:windowBucket - the P14 de-dup key built by detection-service's rules. */
    public static String dedupKey(RuleHitsDetectedEvent event, RuleHitPayload hit) {
        return event.accountId() + ":" + hit.ruleCode() + ":" + bucket(event, hit);
    }

    /** Every Phase 4 rule sets a bucket; fall back to the transaction so a missing one never merges unrelated hits. */
    private static String bucket(RuleHitsDetectedEvent event, RuleHitPayload hit) {
        return hit.windowBucket() != null && !hit.windowBucket().isBlank()
                ? hit.windowBucket()
                : "txn-" + event.transactionId();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize evidence", e);
        }
    }
}
