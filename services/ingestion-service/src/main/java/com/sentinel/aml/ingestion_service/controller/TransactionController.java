package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.dto.IngestionResult;
import com.sentinel.aml.ingestion_service.dto.TransactionRequest;
import com.sentinel.aml.ingestion_service.service.TransactionIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.TRANSACTIONS)
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private final TransactionIngestionService ingestionService;

    /**
     * POST /api/v1/transactions (blueprint path). "/ingest" kept as an alias for existing clients.
     * 202 on first submission; 200 with the ORIGINAL transactionId when the same Idempotency-Key is replayed.
     */
    @PostMapping({"", "/ingest"})
    public ResponseEntity<ApiResponse<String>> ingestTransaction(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {

        IngestionResult result = ingestionService.ingestTransaction(request, idempotencyKey);

        ApiResponse<String> body = ApiResponse.<String>builder()
                .success(true)
                .message(result.replayed()
                        ? "Duplicate request: returning the original transaction"
                        : "Transaction accepted for asynchronous processing")
                .data(result.transactionId())
                .build();

        return result.replayed()
                ? ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body)
                : ResponseEntity.accepted().body(body);
    }
}
