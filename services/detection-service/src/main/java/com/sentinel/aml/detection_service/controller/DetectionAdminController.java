package com.sentinel.aml.detection_service.controller;

import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.detection_service.job.BaselineJob;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import com.sentinel.aml.detection_service.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/** Operational endpoints. Phase 6: restrict to RULE_ADMIN. Rule CRUD lives in rules.RuleAdminController (P13). */
@RestController
@RequiredArgsConstructor
public class DetectionAdminController {

    private final RuleCacheService ruleCache;
    private final WatchlistService watchlist;
    private final BaselineJob baselineJob;

    @PostMapping("/api/v1/detection/rules/reload")
    public ResponseEntity<ApiResponse<String>> reloadRules() {
        ruleCache.refreshRuleCache();
        watchlist.refresh();
        return ResponseEntity.ok(ApiResponse.<String>builder().success(true).message("Rules and watchlist reloaded").build());
    }

    /** Run the nightly baseline job now (handy for demos and the seed scenarios). */
    @PostMapping("/api/v1/detection/baseline/recompute")
    public ResponseEntity<ApiResponse<Integer>> recomputeBaselines() {
        int n = baselineJob.recomputeAll();
        return ResponseEntity.ok(ApiResponse.<Integer>builder().success(true)
                .message("Baselines recomputed").data(n).build());
    }
}
