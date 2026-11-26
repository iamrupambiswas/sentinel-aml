package com.sentinel.aml.alert_case_service.audit;

import com.sentinel.aml.alert_case_service.cases.CaseDtos;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseDetail;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseStatus;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CreateCaseRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.UpdateCaseRequest;
import com.sentinel.aml.alert_case_service.cases.CaseService;
import com.sentinel.aml.alert_case_service.service.AlertIngestService;
import com.sentinel.aml.common.audit.AuditEvent;
import com.sentinel.aml.common.event.RuleHitPayload;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 7b against a real MySQL: the guarantees come from InnoDB row locks and triggers, which mocks can't show.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
class AuditAndCasesIntegrationTest {

    @Container
    // log-bin-trust-function-creators: lets a non-SUPER user create V2's audit triggers (binary logging is on)
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("alert_case_db")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useAffectedRows=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }

    @Autowired AuditLog auditLog;
    @Autowired CaseService cases;
    @Autowired AlertIngestService ingest;
    @Autowired JdbcTemplate jdbc;

    /** Root connection for the admin-only steps (TRUNCATE, DROP/CREATE TRIGGER): the app user can't do them. */
    private static JdbcTemplate root() {
        // One connection, so session settings (FOREIGN_KEY_CHECKS) apply to the following statements
        var ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword(), true);
        return new JdbcTemplate(ds);
    }

    @BeforeEach
    void clean() {
        // TRUNCATE does not fire DELETE triggers: test-only reset of the append-only table
        JdbcTemplate admin = root();
        admin.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : List.of("audit_event", "case_note", "case_alert", "aml_case", "alert_hit", "alert")) {
            admin.execute("TRUNCATE TABLE " + t);
        }
        admin.execute("SET FOREIGN_KEY_CHECKS = 1");
        admin.update("UPDATE audit_chain_head SET last_seq = 0, last_hash = ? WHERE id = 1", AuditChain.GENESIS);
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String user, String... roles) {
        String[] authorities = new String[roles.length];
        for (int i = 0; i < roles.length; i++) authorities[i] = "ROLE_" + roles[i];
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, authorities));
    }

    private static AuditEvent event(int i) {
        return AuditEvent.of("test-service", "user" + (i % 5), "TEST_ACTION", "THING", "T-" + i, Map.of("i", i, "z", "last", "a", "first"));
    }

    @Test
    void concurrentAppendsFormOneGaplessValidChainAndDuplicatesAreStoredOnce() throws Exception {
        List<AuditEvent> events = new ArrayList<>();
        for (int i = 0; i < 200; i++) events.add(event(i));
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (AuditEvent e : events) tasks.add(() -> auditLog.append(e));
        for (int i = 0; i < 40; i++) {               // redeliveries of the first 40, racing the originals
            AuditEvent e = events.get(i);
            tasks.add(() -> auditLog.append(e));
        }
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            long appended = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) if (f.get()) appended++;
            assertThat(appended).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT MAX(seq) FROM audit_event", Long.class)).isEqualTo(200);
        AuditLog.VerifyResult r = auditLog.verify();
        assertThat(r.valid()).isTrue();
        assertThat(r.checked()).isEqualTo(200);
    }

    @Test
    void historyCannotBeEditedAndEditsThatBypassTheTriggerAreDetected() {
        for (int i = 0; i < 10; i++) auditLog.append(event(i));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET actor = 'mallory' WHERE seq = 5"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE seq = 10"))
                .hasMessageContaining("append-only");

        // Someone with DDL rights drops the trigger and rewrites an entry: the chain shows exactly where
        JdbcTemplate admin = root();
        admin.execute("DROP TRIGGER trg_audit_event_no_update");
        try {
            admin.update("UPDATE audit_event SET actor = 'mallory' WHERE seq = 5");
            AuditLog.VerifyResult r = auditLog.verify();
            assertThat(r.valid()).isFalse();
            assertThat(r.firstBrokenSeq()).isEqualTo(5);
            assertThat(r.problem()).contains("changed");
        } finally {
            admin.execute("CREATE TRIGGER trg_audit_event_no_update BEFORE UPDATE ON audit_event FOR EACH ROW "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only: UPDATE is not allowed'");
        }
    }

    @Test
    void caseWorkflowEnforcesRolesAndRecordsEveryStepInTheAuditChain() {
        String alertA = newAlert("ACC-CASE-1", "STRUCTURING", "75.00");
        String alertB = newAlert("ACC-CASE-1", "CTR_THRESHOLD", "30.00");
        String alertC = newAlert("ACC-CASE-2", "CTR_THRESHOLD", "30.00");

        loginAs("analyst1", "ANALYST");
        CaseDetail created = cases.create(new CreateCaseRequest("Structuring on ACC-CASE-1", List.of(alertA, alertB), null, "First look"));
        long id = created.summary().id();
        assertThat(created.summary().priority()).isEqualTo(CaseDtos.Priority.HIGH);       // from the 75.00 alert
        assertThat(created.summary().alertCount()).isEqualTo(2);
        assertThat(created.notes()).hasSize(1);

        // An alert belongs to at most one case
        assertThatThrownBy(() -> cases.create(new CreateCaseRequest("Dup", List.of(alertA), null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already in");

        cases.update(id, new UpdateCaseRequest(CaseStatus.INVESTIGATING, "analyst1", null, null, 0));
        // List views: open cases, my cases, filtered by status
        assertThat(cases.search(null, null, true, 0, 20).getContent()).extracting(CaseDtos.CaseSummary::id).containsExactly(id);
        assertThat(cases.search(null, "analyst1", false, 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(cases.search(CaseStatus.CLOSED_REPORTED, null, false, 0, 20).getTotalElements()).isZero();
        cases.addAlert(id, new CaseDtos.AddAlertRequest(alertC));
        cases.addNote(id, "Source of funds requested");

        // Stale version -> 409; an analyst may not close -> 403
        assertThatThrownBy(() -> cases.update(id, new UpdateCaseRequest(null, "senior1", null, null, 0)))
                .hasMessageContaining("changed by someone else");
        assertThatThrownBy(() -> cases.update(id, new UpdateCaseRequest(CaseStatus.CLOSED_REPORTED, null, null, "Filed", 1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("SENIOR_ANALYST");

        loginAs("senior1", "SENIOR_ANALYST", "ANALYST");
        assertThatThrownBy(() -> cases.update(id, new UpdateCaseRequest(CaseStatus.CLOSED_REPORTED, null, null, " ", 1)))
                .hasMessageContaining("resolution");
        CaseDetail closed = cases.update(id, new UpdateCaseRequest(CaseStatus.CLOSED_REPORTED, null, null, "Reported to FIU-IND", 1));
        assertThat(closed.summary().status()).isEqualTo(CaseStatus.CLOSED_REPORTED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.allowedNextStatuses()).isEmpty();

        // Closed is final
        assertThatThrownBy(() -> cases.addNote(id, "late note")).hasMessageContaining("closed");

        assertThat(closed.history()).extracting(AuditLog.AuditEntryView::action).containsExactly(
                "CASE_CLOSED", "CASE_NOTE_ADDED", "CASE_ALERT_ADDED", "CASE_ASSIGNED", "CASE_STATUS_CHANGED", "CASE_CREATED");
        assertThat(closed.history().get(0).actor()).isEqualTo("senior1");
        assertThat(closed.history().get(5).actor()).isEqualTo("analyst1");
        assertThat(auditLog.verify().valid()).isTrue();
        assertThat(cases.search(null, null, true, 0, 20).getContent()).isEmpty();   // closed cases leave the open view
    }

    private String newAlert(String account, String rule, String score) {
        RuleHitsDetectedEvent e = new RuleHitsDetectedEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), account,
                List.of(new RuleHitPayload(rule, new BigDecimal("30"), "test hit", List.of("t1"), "2026-11-21")),
                new BigDecimal(score), "test", Instant.parse("2026-11-21T10:00:00Z"), Instant.parse("2026-11-21T10:00:01Z"));
        ingest.ingest(e);
        return jdbc.queryForObject("SELECT id FROM alert WHERE dedup_key = ?", String.class, account + ":" + rule + ":2026-11-21");
    }
}
