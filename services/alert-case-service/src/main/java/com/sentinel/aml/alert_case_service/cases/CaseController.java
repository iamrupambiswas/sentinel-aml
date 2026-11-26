package com.sentinel.aml.alert_case_service.cases;

import com.sentinel.aml.alert_case_service.cases.CaseDtos.AddAlertRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.AddNoteRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseDetail;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseStatus;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CaseSummary;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.CreateCaseRequest;
import com.sentinel.aml.alert_case_service.cases.CaseDtos.UpdateCaseRequest;
import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Investigation cases (Phase 7b). ANALYST+; closing a case needs SENIOR_ANALYST (checked in CaseService). */
@RestController
@RequestMapping(ApiRoutes.CASES)
@RequiredArgsConstructor
public class CaseController {

    private final CaseService cases;

    @PostMapping
    public ResponseEntity<ApiResponse<CaseDetail>> create(@Valid @RequestBody CreateCaseRequest request) {
        CaseDetail c = cases.create(request);
        return respond(HttpStatus.CREATED, c, c.summary().caseNumber() + " opened");
    }

    /** Open work first, then priority, then most recently updated. open=true hides closed cases. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CaseSummary>>> list(
            @RequestParam(name = "status", required = false) CaseStatus status,
            @RequestParam(name = "assignee", required = false) String assignee,
            @RequestParam(name = "open", defaultValue = "false") boolean openOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return respond(HttpStatus.OK, cases.search(status, assignee, openOnly, Math.max(page, 0), Math.min(Math.max(size, 1), 200)), null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseDetail>> get(@PathVariable("id") long id) {
        return respond(HttpStatus.OK, cases.get(id), null);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseDetail>> update(@PathVariable("id") long id, @Valid @RequestBody UpdateCaseRequest request) {
        return respond(HttpStatus.OK, cases.update(id, request), "Case updated");
    }

    @PostMapping("/{id}/alerts")
    public ResponseEntity<ApiResponse<CaseDetail>> addAlert(@PathVariable("id") long id, @Valid @RequestBody AddAlertRequest request) {
        return respond(HttpStatus.OK, cases.addAlert(id, request), "Alert added");
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<CaseDetail>> addNote(@PathVariable("id") long id, @Valid @RequestBody AddNoteRequest request) {
        return respond(HttpStatus.CREATED, cases.addNote(id, request.body()), "Note added");
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, T data, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder().success(true).message(message).data(data).build());
    }
}
