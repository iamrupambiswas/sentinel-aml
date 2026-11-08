package com.sentinel.aml.controller;

import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.constants.ApiRoutes;
import com.sentinel.aml.seeder.DataSeederService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.API_V1 + "/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataSeederService dataSeederService;

    @PostMapping("/seed-data")
    public ResponseEntity<ApiResponse<String>> seedDatabase() {
        String result = dataSeederService.seedDatabase();

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Data seeding operation completed")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }
}