package com.sentinel.aml.detection_service.rules;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleUpdateRequest;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleVersionView;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** Rule admin API (P13). Reads need ANALYST, changes need ADMIN (config/SecurityRoutes); changedBy is the JWT username. */
@RestController
@RequestMapping(ApiRoutes.RULES)
@RequiredArgsConstructor
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;

    /** All rules (active and inactive) with the version this pod has loaded. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RuleView>>> list() {
        return ok(ruleAdminService.list(), null);
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleView>> get(@PathVariable("code") String code) {
        return ok(ruleAdminService.get(code), null);
    }

    @GetMapping("/{code}/versions")
    public ResponseEntity<ApiResponse<List<RuleVersionView>>> history(@PathVariable("code") String code) {
        return ok(ruleAdminService.history(code), null);
    }

    @PutMapping("/{code}")
    public ResponseEntity<ApiResponse<RuleView>> update(@PathVariable("code") String code,
                                                        @Valid @RequestBody RuleUpdateRequest request,
                                                        Principal principal) {
        RuleView view = ruleAdminService.update(code, request, principal.getName());
        return ok(view, "Rule " + code + " saved as version " + view.version()
                + "; broadcast on sentinel.rules.changed, live on all pods in seconds");
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.<T>builder().success(true).message(message).data(data).build());
    }
}
