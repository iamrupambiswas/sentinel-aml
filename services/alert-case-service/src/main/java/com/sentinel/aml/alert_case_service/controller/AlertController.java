package com.sentinel.aml.alert_case_service.controller;

import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertDetail;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertStats;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertSummary;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.StatusChangeRequest;
import com.sentinel.aml.alert_case_service.entity.AlertStatus;
import com.sentinel.aml.alert_case_service.service.AlertQueryService;
import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import jakarta.validation.Valid;
import com.sentinel.aml.alert_case_service.live.AlertStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analyst API. ANALYST role and up (config/SecurityRoutes). */
@RestController
@RequestMapping(ApiRoutes.ALERTS)
@RequiredArgsConstructor
public class AlertController {

    private final AlertQueryService alertService;
    private final AlertStreamService alertStream;

    /** Highest risk first, then most recently active. All filters are optional. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AlertSummary>>> list(
            @RequestParam(name = "status", required = false) AlertStatus status,
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "ruleCode", required = false) String ruleCode,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Order.desc("riskScore"), Sort.Order.desc("lastDetectedAt")));
        return ResponseEntity.ok(ApiResponse.<Page<AlertSummary>>builder().success(true)
                .data(alertService.search(status, accountId, ruleCode, pageable)).build());
    }

    /**
     * Live alert changes as Server-Sent Events (Phase 7): event "alert" carries an AlertChangedEvent.
     * The console re-reads the queue when one arrives.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return alertStream.subscribe();
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AlertStats>> stats() {
        return ResponseEntity.ok(ApiResponse.<AlertStats>builder().success(true).data(alertService.stats()).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlertDetail>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.<AlertDetail>builder().success(true).data(alertService.get(id)).build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AlertSummary>> changeStatus(@PathVariable("id") String id,
                                                                  @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.<AlertSummary>builder().success(true)
                .message("Alert moved to " + request.status())
                .data(alertService.changeStatus(id, request)).build());
    }
}
