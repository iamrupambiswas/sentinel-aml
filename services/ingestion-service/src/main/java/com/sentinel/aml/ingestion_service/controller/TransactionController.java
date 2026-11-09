package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.dto.TransactionRequest;
import com.sentinel.aml.ingestion_service.service.TransactionIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.TRANSACTIONS)
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionIngestionService ingestionService;

    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<String>> ingestTransaction(
            @RequestHeader("Idempotency-Key") String idempotencyKey, // <-- Required Header
            @Valid @RequestBody TransactionRequest request) {

        try {
            String transactionId = ingestionService.ingestTransaction(request, idempotencyKey);

            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .message("Transaction accepted for asynchronous processing")
                    .data(transactionId)
                    .build();

            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException ex) {
            // Catch the idempotency rejection
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(response);
        }
    }
}