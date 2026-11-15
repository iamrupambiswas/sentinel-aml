package com.sentinel.aml.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.risk.AccountRiskSyncService;
import com.sentinel.aml.seeder.DataSeederService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.ADMIN)
@RequiredArgsConstructor
public class AdminController {

    private final DataSeederService dataSeederService;
    private final AccountRiskSyncService riskSyncService;

    @PostMapping("/seed-data")
    public ResponseEntity<ApiResponse<String>> seedDatabase() {
        String result = dataSeederService.seedDatabase();
        // Seeding commits first (its own transaction); then mirror every rating into Redis
        int synced = riskSyncService.syncAll();
        result = result + " Risk ratings synced to Redis for " + synced + " account(s).";

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Data seeding operation completed")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    /** Full resync of every account's effective risk rating into Redis (repairs a missed write). */
    @PostMapping("/risk-sync")
    public ResponseEntity<ApiResponse<Integer>> syncRiskRatings() {
        int synced = riskSyncService.syncAll();
        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .success(true)
                .message("Effective risk ratings synced to Redis")
                .data(synced)
                .build());
    }
}
