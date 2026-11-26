package com.sentinel.aml.alert_case_service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sentinel.aml.common.audit.AuditEvent;
import com.sentinel.aml.common.audit.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tamper-evident audit log (Phase 7b): appends entries to a SHA-256 hash chain and verifies it.
 *
 * <h3>Ordering</h3>
 * Every append locks the single {@code audit_chain_head} row ({@code SELECT ... FOR UPDATE}) and holds it
 * until its transaction commits. Appends from several pods and threads therefore get consecutive sequence
 * numbers and each one sees the previous committed hash, never a sibling's uncommitted one.
 *
 * <h3>Append-only</h3>
 * Triggers reject UPDATE and DELETE on {@code audit_event}; if someone bypasses them (drops a trigger, edits
 * the data files), {@link #verify()} finds the first entry whose hash no longer matches.
 */
@Service
@Slf4j
public class AuditLog {

    public static final String SERVICE = "alert-case-service";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper canonicalJson;
    private final Clock clock;

    public AuditLog(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        // Same JSON for the same map whatever its key order: the hashed bytes must be reproducible
        this.canonicalJson = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    public record AuditEntryView(long seq, String eventId, LocalDateTime occurredAt, LocalDateTime recordedAt,
                                 String service, String actor, String action, String entityType, String entityId,
                                 String details, String hash) {}

    public record VerifyResult(boolean valid, long checked, Long firstBrokenSeq, String problem, String headHash) {}

    /** Records an action of this service by the current user, inside the caller's transaction. */
    @Transactional
    public void record(String action, String entityType, String entityId, Map<String, ?> details) {
        append(AuditEvent.of(SERVICE, CurrentUser.name(), action, entityType, entityId, details));
    }

    /** @return false when an event with this id is already in the chain (broker redelivery) */
    @Transactional
    public boolean append(AuditEvent e) {
        require(e.eventId(), "eventId");
        require(e.service(), "service");
        require(e.actor(), "actor");
        require(e.action(), "action");
        require(e.entityType(), "entityType");
        require(e.entityId(), "entityId");

        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE", none);

        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_id = :id",
                new MapSqlParameterSource("id", e.eventId()), Integer.class);
        if (existing != null && existing > 0) {
            log.info("Audit event {} already recorded, ignoring duplicate", e.eventId());
            return false;
        }

        long seq = ((Number) head.get("last_seq")).longValue() + 1;
        String prevHash = (String) head.get("last_hash");
        Instant occurred = (e.occurredAt() != null ? e.occurredAt() : clock.instant()).truncatedTo(ChronoUnit.MICROS);
        LocalDateTime occurredAt = LocalDateTime.ofInstant(occurred, ZoneOffset.UTC);
        String details = toJson(e.details() == null ? Map.of() : e.details());

        AuditChain.Entry entry = new AuditChain.Entry(seq, e.eventId(), occurredAt, e.service(), e.actor(),
                e.action(), e.entityType(), e.entityId(), details, prevHash);
        String hash = AuditChain.hash(entry);

        jdbc.update("""
                INSERT INTO audit_event (seq, event_id, occurred_at, recorded_at, service, actor, action,
                                         entity_type, entity_id, details, prev_hash, hash)
                VALUES (:seq, :eventId, :occurredAt, :recordedAt, :service, :actor, :action,
                        :entityType, :entityId, :details, :prevHash, :hash)
                """, new MapSqlParameterSource()
                .addValue("seq", seq).addValue("eventId", e.eventId()).addValue("occurredAt", occurredAt)
                .addValue("recordedAt", LocalDateTime.now(clock)).addValue("service", e.service())
                .addValue("actor", e.actor()).addValue("action", e.action()).addValue("entityType", e.entityType())
                .addValue("entityId", e.entityId()).addValue("details", details)
                .addValue("prevHash", prevHash).addValue("hash", hash));
        jdbc.update("UPDATE audit_chain_head SET last_seq = :seq, last_hash = :hash WHERE id = 1",
                new MapSqlParameterSource("seq", seq).addValue("hash", hash));
        return true;
    }

    /**
     * Recomputes the whole chain from the first entry. Reports the first entry that was changed (hash
     * mismatch), removed (gap in seq or broken link) or cut off at the end (head ahead of the rows).
     */
    @Transactional(readOnly = true)
    public VerifyResult verify() {
        String prev = AuditChain.GENESIS;
        long expected = 1;
        long checked = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT seq, event_id, occurred_at, service, actor, action, entity_type, entity_id, details, prev_hash, hash
                      FROM audit_event WHERE seq >= :from ORDER BY seq LIMIT 1000
                    """, new MapSqlParameterSource("from", expected));
            if (rows.isEmpty()) break;
            for (Map<String, Object> r : rows) {
                long seq = ((Number) r.get("seq")).longValue();
                if (seq != expected) return broken(checked, expected, "entry " + expected + " is missing");
                if (!prev.equals(r.get("prev_hash"))) return broken(checked, seq, "link to the previous entry is broken");
                String recomputed = AuditChain.hash(new AuditChain.Entry(seq, (String) r.get("event_id"),
                        toLocal(r.get("occurred_at")), (String) r.get("service"), (String) r.get("actor"),
                        (String) r.get("action"), (String) r.get("entity_type"), (String) r.get("entity_id"),
                        (String) r.get("details"), (String) r.get("prev_hash")));
                if (!recomputed.equals(r.get("hash"))) return broken(checked, seq, "entry content was changed");
                prev = recomputed;
                expected++;
                checked++;
            }
        }
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1",
                new MapSqlParameterSource());
        if (((Number) head.get("last_seq")).longValue() != checked || !prev.equals(head.get("last_hash"))) {
            return broken(checked, checked + 1, "entries at the end are missing (head is at " + head.get("last_seq") + ")");
        }
        return new VerifyResult(true, checked, null, null, prev);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryView> search(String entityType, String entityId, String actor, String action, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (hasText(entityType)) { where.append(" AND entity_type = :entityType"); p.addValue("entityType", entityType); }
        if (hasText(entityId)) { where.append(" AND entity_id = :entityId"); p.addValue("entityId", entityId); }
        if (hasText(actor)) { where.append(" AND actor = :actor"); p.addValue("actor", actor); }
        if (hasText(action)) { where.append(" AND action = :action"); p.addValue("action", action); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event" + where, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<AuditEntryView> content = jdbc.query("""
                SELECT seq, event_id, occurred_at, recorded_at, service, actor, action, entity_type, entity_id, details, hash
                  FROM audit_event""" + where + " ORDER BY seq DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new AuditEntryView(rs.getLong("seq"), rs.getString("event_id"),
                        rs.getObject("occurred_at", LocalDateTime.class), rs.getObject("recorded_at", LocalDateTime.class),
                        rs.getString("service"), rs.getString("actor"), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("details"), rs.getString("hash")));
        return new PageImpl<>(new ArrayList<>(content), pageable, total == null ? 0 : total);
    }

    private static VerifyResult broken(long checked, long seq, String problem) {
        log.error("AUDIT CHAIN BROKEN at seq {}: {}", seq, problem);
        return new VerifyResult(false, checked, seq, problem, null);
    }

    private String toJson(Map<String, ?> details) {
        try {
            return canonicalJson.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit details are not serializable", ex);
        }
    }

    private static LocalDateTime toLocal(Object v) {
        return v instanceof LocalDateTime l ? l : ((java.sql.Timestamp) v).toLocalDateTime();
    }

    private static void require(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Audit event without " + field);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
