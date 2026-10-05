package com.flightlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.StudentApplicationRequest;
import com.flightlog.web.ApiDtos.StudentApplicationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentApplicationController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationPeriodService service;
    private final ObjectMapper objectMapper;

    public StudentApplicationController(ApplicationPeriodService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/application-periods/{periodId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentApplicationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @Valid @RequestBody StudentApplicationRequest request) {
        return service.createApplication(caller, periodId, objectMapper.valueToTree(request));
    }

    @GetMapping("/api/application-periods/{periodId}/applications/me")
    public StudentApplicationResponse me(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId) {
        return service.myApplication(caller, periodId);
    }

    @GetMapping("/api/application-periods/{periodId}/applications")
    public PageResponse<StudentApplicationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.applications(caller, periodId, Math.max(page, 0), normalizeSize(size));
    }

    @PutMapping("/api/applications/{id}")
    public StudentApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody StudentApplicationRequest request) {
        return service.updateApplication(caller, id, objectMapper.valueToTree(request));
    }

    @DeleteMapping("/api/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        service.withdrawApplication(caller, id);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
