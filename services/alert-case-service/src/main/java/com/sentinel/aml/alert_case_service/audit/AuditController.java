package com.sentinel.aml.alert_case_service.audit;

import com.sentinel.aml.alert_case_service.audit.AuditLog.AuditEntryView;
import com.sentinel.aml.alert_case_service.audit.AuditLog.VerifyResult;
import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log API (Phase 7b): browse is SENIOR_ANALYST+, verify is ADMIN (config/SecurityRoutes). */
@RestController
@RequestMapping(ApiRoutes.AUDIT)
@RequiredArgsConstructor
public class AuditController {

    private final AuditLog auditLog;

    /** Newest first. All filters optional, e.g. ?entityType=CUSTOMER&entityId=... or ?actor=admin1 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditEntryView>>> search(
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "entityId", required = false) String entityId,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.<Page<AuditEntryView>>builder().success(true)
                .data(auditLog.search(entityType, entityId, actor, action,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))).build());
    }

    /** Recomputes every hash. valid=false names the first entry that was changed or removed. */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyResult>> verify() {
        VerifyResult r = auditLog.verify();
        return ResponseEntity.ok(ApiResponse.<VerifyResult>builder().success(true)
                .message(r.valid() ? "Audit chain intact: " + r.checked() + " entries verified"
                        : "AUDIT CHAIN BROKEN at entry " + r.firstBrokenSeq() + ": " + r.problem())
                .data(r).build());
    }
}
