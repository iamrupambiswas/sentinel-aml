package com.sentinel.aml.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Hides null fields (like 'errors' on success)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
}