package com.sentinel.aml.ingestion_service.exception;

import com.sentinel.aml.common.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Runs before the catch-all handler in common-lib. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IngestionExceptionHandler {

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(InvalidTransactionException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiResponse.<Void>builder()
                .success(false).message("Transaction rejected").errors(List.of(ex.getMessage())).build());
    }

    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<ApiResponse<Void>> handleInProgress(RequestInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<Void>builder()
                .success(false).message(ex.getMessage()).build());
    }
}
