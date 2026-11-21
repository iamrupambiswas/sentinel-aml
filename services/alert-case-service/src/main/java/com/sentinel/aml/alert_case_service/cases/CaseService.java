package com.sentinel.aml.alert_case_service.cases;

import com.sentinel.aml.alert_case_service.audit.AuditLog;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.AddAlertRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseDetail;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseStatus;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseSummary;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CreateCaseRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.NoteView;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.Priority;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.UpdateCaseRequest;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertSummary;
import com.sentinel.aml.alert_case_service.repository.AlertRepository;
import com.sentinel.aml.common.audit.CurrentUser;
import com.sentinel.aml.common.security.SentinelSecurityConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Investigation cases (Phase 7b). A case groups alerts (each alert belongs to at most one case), carries
 * notes, an assignee and a status. Every change is recorded in the hash-chained audit log in the SAME
 * transaction, so a case change and its audit entry are committed together or not at all.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    static final String SELECT_SUMMARY = """
            SELECT c.id, c.title, c.status, c.priority, c.assignee, c.created_by, c.created_at, c.updated_at, c.version,
                   COUNT(ca.alert_id) AS alert_count, MAX(a.risk_score) AS max_risk
              FROM aml_case c
              LEFT JOIN case_alert ca ON ca.case_id = c.id
              LEFT JOIN alert a ON a.id = ca.alert_id
            """;
    static final String GROUP = " GROUP BY c.id, c.title, c.status, c.priority, c.assignee, c.created_by, c.created_at, c.updated_at, c.version";

    private static final RowMapper<CaseSummary> SUMMARY = (rs, i) -> new CaseSummary(
            rs.getLong("id"), CaseDtos.caseNumber(rs.getLong("id")), rs.getString("title"),
            CaseStatus.valueOf(rs.getString("status")), Priority.valueOf(rs.getString("priority")),
            rs.getString("assignee"), rs.getString("created_by"),
            rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class),
            rs.getInt("alert_count"), rs.getBigDecimal("max_risk"), rs.getInt("version"));

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertRepository alertRepository;
    private final AuditLog auditLog;
    private final Clock clock;

    @Transactional
    public CaseDetail create(CreateCaseRequest req) {
        List<String> alertIds = new ArrayList<>(new LinkedHashSet<>(req.alertIds().stream().map(String::trim).toList()));
        Map<String, BigDecimal> risks = alertRisks(alertIds);
        List<String> missing = alertIds.stream().filter(id -> !risks.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert(s) not found: " + missing);
        }
        rejectAlertsInOtherCases(alertIds);

        String actor = CurrentUser.name();
        LocalDateTime now = LocalDateTime.now(clock);
        Priority priority = req.priority() != null ? req.priority()
                : Priority.fromRisk(risks.values().stream().max(Comparator.naturalOrder()).orElse(null));

        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO aml_case (title, status, priority, assignee, created_by, created_at, updated_at, version)
                VALUES (:title, 'OPEN', :priority, NULL, :actor, :now, :now, 0)
                """, new MapSqlParameterSource().addValue("title", req.title().trim())
                .addValue("priority", priority.name()).addValue("actor", actor).addValue("now", now), key, new String[]{"id"});
        long id = Objects.requireNonNull(key.getKey()).longValue();

        for (String alertId : alertIds) attach(id, alertId, actor, now);
        if (req.note() != null && !req.note().isBlank()) insertNote(id, actor, req.note().trim(), now);

        auditLog.record("CASE_CREATED", "CASE", CaseDtos.caseNumber(id),
                Map.of("title", req.title().trim(), "alerts", alertIds, "priority", priority.name()));
        log.info("{} opened by {} with {} alert(s)", CaseDtos.caseNumber(id), actor, alertIds.size());
        return get(id);
    }

    @Transactional(readOnly = true)
    public Page<CaseSummary> search(CaseStatus status, String assignee, boolean openOnly, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (status != null) { where.append(" AND c.status = :status"); p.addValue("status", status.name()); }
        if (openOnly) where.append(" AND c.status IN ('OPEN', 'INVESTIGATING')");
        if (assignee != null && !assignee.isBlank()) { where.append(" AND c.assignee = :assignee"); p.addValue("assignee", assignee); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM aml_case c" + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        // Open work first, then highest priority, then most recently touched
        List<CaseSummary> content = jdbc.query(SELECT_SUMMARY + where + GROUP
                + " ORDER BY (c.status LIKE 'CLOSED%'), FIELD(c.priority, 'HIGH', 'MEDIUM', 'LOW'), c.updated_at DESC"
                + " LIMIT :limit OFFSET :offset", p, SUMMARY);
        return new PageImpl<>(content, PageRequest.of(page, size), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public CaseDetail get(long id) {
        CaseSummary summary = summary(id);
        Map<String, Object> extra = jdbc.queryForMap("SELECT resolution, closed_at FROM aml_case WHERE id = :id",
                new MapSqlParameterSource("id", id));
        List<String> alertIds = jdbc.queryForList("SELECT alert_id FROM case_alert WHERE case_id = :id ORDER BY added_at",
                new MapSqlParameterSource("id", id), String.class);
        List<AlertSummary> alerts = alertRepository.findAllById(alertIds).stream().map(AlertSummary::from)
                .sorted(Comparator.comparing(AlertSummary::riskScore).reversed()).toList();
        List<NoteView> notes = jdbc.query("SELECT id, author, body, created_at FROM case_note WHERE case_id = :id ORDER BY created_at DESC",
                new MapSqlParameterSource("id", id), (rs, i) -> new NoteView(rs.getLong("id"), rs.getString("author"),
                        rs.getString("body"), rs.getObject("created_at", LocalDateTime.class)));
        var history = auditLog.search("CASE", CaseDtos.caseNumber(id), null, null, PageRequest.of(0, 100)).getContent();
        Object closedAt = extra.get("closed_at");
        return new CaseDetail(summary, (String) extra.get("resolution"),
                closedAt instanceof LocalDateTime l ? l : closedAt == null ? null : ((java.sql.Timestamp) closedAt).toLocalDateTime(),
                List.copyOf(summary.status().allowedNext()), alerts, notes, history);
    }

    @Transactional
    public CaseDetail update(long id, UpdateCaseRequest req) {
        Map<String, Object> row = lockCase(id);
        int version = ((Number) row.get("version")).intValue();
        if (req.expectedVersion() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CaseDtos.caseNumber(id)
                    + " was changed by someone else (version " + version + ", you had " + req.expectedVersion() + "). Reload and retry.");
        }
        CaseStatus current = CaseStatus.valueOf((String) row.get("status"));
        if (current.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CaseDtos.caseNumber(id) + " is closed; closed cases are final");
        }

        String actor = CurrentUser.name();
        LocalDateTime now = LocalDateTime.now(clock);
        String number = CaseDtos.caseNumber(id);
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("now", now);
        List<String> sets = new ArrayList<>();

        if (req.status() != null && req.status() != current) {
            if (!current.allowedNext().contains(req.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot move " + number + " from " + current + " to " + req.status() + ". Allowed: " + current.allowedNext());
            }
            if (req.status().isClosed()) {
                if (!CurrentUser.hasRole(SentinelSecurityConfig.SENIOR_ANALYST)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Closing a case needs the SENIOR_ANALYST role");
                }
                if (req.resolution() == null || req.resolution().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closing a case needs a resolution");
                }
                sets.add("closed_at = :now");
                sets.add("resolution = :resolution");
                p.addValue("resolution", req.resolution().trim());
            }
            sets.add("status = :status");
            p.addValue("status", req.status().name());
            auditLog.record(req.status().isClosed() ? "CASE_CLOSED" : "CASE_STATUS_CHANGED", "CASE", number,
                    req.status().isClosed()
                            ? Map.of("from", current.name(), "to", req.status().name(), "resolution", req.resolution().trim())
                            : Map.of("from", current.name(), "to", req.status().name()));
        }
        String oldAssignee = (String) row.get("assignee");
        if (req.assignee() != null) {
            String newAssignee = req.assignee().isBlank() ? null : req.assignee().trim();
            if (!Objects.equals(newAssignee, oldAssignee)) {
                sets.add("assignee = :assignee");
                p.addValue("assignee", newAssignee);
                auditLog.record("CASE_ASSIGNED", "CASE", number,
                        Map.of("from", oldAssignee == null ? "unassigned" : oldAssignee, "to", newAssignee == null ? "unassigned" : newAssignee));
            }
        }
        if (req.priority() != null && !req.priority().name().equals(row.get("priority"))) {
            sets.add("priority = :priority");
            p.addValue("priority", req.priority().name());
            auditLog.record("CASE_PRIORITY_CHANGED", "CASE", number, Map.of("from", row.get("priority"), "to", req.priority().name()));
        }
        if (sets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to change on " + number);
        }
        jdbc.update("UPDATE aml_case SET " + String.join(", ", sets) + ", updated_at = :now, version = version + 1 WHERE id = :id", p);
        log.info("{} updated by {}: {}", number, actor, sets);
        return get(id);
    }

    @Transactional
    public CaseDetail addAlert(long id, AddAlertRequest req) {
        requireOpen(lockCase(id), id);
        String alertId = req.alertId().trim();
        if (alertRisks(List.of(alertId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + alertId);
        }
        rejectAlertsInOtherCases(List.of(alertId));
        LocalDateTime now = LocalDateTime.now(clock);
        attach(id, alertId, CurrentUser.name(), now);
        touch(id, now);
        auditLog.record("CASE_ALERT_ADDED", "CASE", CaseDtos.caseNumber(id), Map.of("alertId", alertId));
        return get(id);
    }

    @Transactional
    public CaseDetail addNote(long id, String body) {
        requireOpen(lockCase(id), id);
        LocalDateTime now = LocalDateTime.now(clock);
        insertNote(id, CurrentUser.name(), body.trim(), now);
        touch(id, now);
        // The note text is in case_note; the audit entry records that it was written, not what it says
        auditLog.record("CASE_NOTE_ADDED", "CASE", CaseDtos.caseNumber(id), Map.of("length", body.trim().length()));
        return get(id);
    }

    /** The case an alert belongs to, if any (shown on the alert page). */
    @Transactional(readOnly = true)
    public Long caseOfAlert(String alertId) {
        List<Long> ids = jdbc.queryForList("SELECT case_id FROM case_alert WHERE alert_id = :id",
                new MapSqlParameterSource("id", alertId), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ---------------------------------------------------------------- helpers

    private CaseSummary summary(long id) {
        List<CaseSummary> rows = jdbc.query(SELECT_SUMMARY + " WHERE c.id = :id" + GROUP, new MapSqlParameterSource("id", id), SUMMARY);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id);
        return rows.get(0);
    }

    private Map<String, Object> lockCase(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, assignee, priority, version FROM aml_case WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id);
        return rows.get(0);
    }

    private static void requireOpen(Map<String, Object> row, long id) {
        if (CaseStatus.valueOf((String) row.get("status")).isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CaseDtos.caseNumber(id) + " is closed; closed cases are final");
        }
    }

    private Map<String, BigDecimal> alertRisks(List<String> alertIds) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        jdbc.query("SELECT id, risk_score FROM alert WHERE id IN (:ids)", new MapSqlParameterSource("ids", alertIds),
                rs -> { out.put(rs.getString("id"), rs.getBigDecimal("risk_score")); });
        return out;
    }

    /** Friendly 409 before the UNIQUE(alert_id) constraint, which still guards against a race. */
    private void rejectAlertsInOtherCases(List<String> alertIds) {
        List<Map<String, Object>> taken = jdbc.queryForList("SELECT alert_id, case_id FROM case_alert WHERE alert_id IN (:ids)",
                new MapSqlParameterSource("ids", alertIds));
        if (!taken.isEmpty()) {
            Map<String, Object> t = taken.get(0);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alert " + t.get("alert_id") + " is already in "
                    + CaseDtos.caseNumber(((Number) t.get("case_id")).longValue()));
        }
    }

    private void attach(long caseId, String alertId, String actor, LocalDateTime now) {
        jdbc.update("INSERT INTO case_alert (case_id, alert_id, added_by, added_at) VALUES (:c, :a, :by, :now)",
                new MapSqlParameterSource("c", caseId).addValue("a", alertId).addValue("by", actor).addValue("now", now));
    }

    private void insertNote(long caseId, String author, String body, LocalDateTime now) {
        jdbc.update("INSERT INTO case_note (case_id, author, body, created_at) VALUES (:c, :author, :body, :now)",
                new MapSqlParameterSource("c", caseId).addValue("author", author).addValue("body", body).addValue("now", now));
    }

    private void touch(long id, LocalDateTime now) {
        jdbc.update("UPDATE aml_case SET updated_at = :now WHERE id = :id", new MapSqlParameterSource("id", id).addValue("now", now));
    }
}
