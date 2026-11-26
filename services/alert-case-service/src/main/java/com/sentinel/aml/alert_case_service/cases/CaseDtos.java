package com.sentinel.aml.alert_case_service.cases;

import com.sentinel.aml.alert_case_service.audit.AuditLog.AuditEntryView;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Investigation cases (Phase 7b): request/response shapes and the status workflow. */
public final class CaseDtos {

    private CaseDtos() {}

    public enum CaseStatus {
        OPEN, INVESTIGATING, CLOSED_NO_ACTION, CLOSED_REPORTED;

        public boolean isClosed() {
            return this == CLOSED_NO_ACTION || this == CLOSED_REPORTED;
        }

        public Set<CaseStatus> allowedNext() {
            return switch (this) {
                case OPEN -> EnumSet.of(INVESTIGATING, CLOSED_NO_ACTION, CLOSED_REPORTED);
                case INVESTIGATING -> EnumSet.of(CLOSED_NO_ACTION, CLOSED_REPORTED);
                case CLOSED_NO_ACTION, CLOSED_REPORTED -> EnumSet.noneOf(CaseStatus.class);
            };
        }
    }

    public enum Priority {
        LOW, MEDIUM, HIGH;

        /** Same bands as the console's risk colours. */
        public static Priority fromRisk(BigDecimal maxRisk) {
            if (maxRisk == null) return MEDIUM;
            if (maxRisk.compareTo(BigDecimal.valueOf(70)) >= 0) return HIGH;
            if (maxRisk.compareTo(BigDecimal.valueOf(40)) >= 0) return MEDIUM;
            return LOW;
        }
    }

    /** @param priority optional: derived from the highest alert risk score when omitted */
    public record CreateCaseRequest(@NotBlank @Size(max = 200) String title,
                                    @NotEmpty @Size(max = 100) List<@NotBlank String> alertIds,
                                    Priority priority,
                                    @Size(max = 5000) String note) {}

    /**
     * Every field optional except expectedVersion. assignee "" = unassign.
     * Closing (CLOSED_*) needs SENIOR_ANALYST and a resolution.
     */
    public record UpdateCaseRequest(CaseStatus status, @Size(max = 100) String assignee, Priority priority,
                                    @Size(max = 5000) String resolution, @NotNull Integer expectedVersion) {}

    public record AddAlertRequest(@NotBlank String alertId) {}

    public record AddNoteRequest(@NotBlank @Size(max = 5000) String body) {}

    public record CaseSummary(long id, String caseNumber, String title, CaseStatus status, Priority priority,
                              String assignee, String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                              int alertCount, BigDecimal maxRisk, int version) {}

    public record NoteView(long id, String author, String body, LocalDateTime createdAt) {}

    public record CaseDetail(CaseSummary summary, String resolution, LocalDateTime closedAt,
                             List<CaseStatus> allowedNextStatuses, List<AlertSummary> alerts,
                             List<NoteView> notes, List<AuditEntryView> history) {}

    public static String caseNumber(long id) {
        return String.format("CASE-%06d", id);
    }
}
