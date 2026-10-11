package com.flightlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.ApplicationPeriodResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.ApplicationPeriodRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "School years and applications")
public class ApplicationPeriodController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationPeriodService service;
    private final ObjectMapper objectMapper;

    public ApplicationPeriodController(ApplicationPeriodService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/school-years")
    @Operation(summary = "List available school years.")
    public PageResponse<String> schoolYears(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.schoolYears(Math.max(page, 0), normalizeSize(size));
    }

    @PostMapping("/api/application-periods")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationPeriodResponse createPeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ApplicationPeriodRequest request) {
        return service.create(caller, objectMapper.valueToTree(request));
    }

    @GetMapping("/api/application-periods")
    public PageResponse<ApplicationPeriodResponse> listPeriods(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(caller, Math.max(page, 0), normalizeSize(size));
    }

    @GetMapping("/api/application-periods/{id}")
    public ApplicationPeriodResponse getPeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        return service.get(caller, id);
    }

    @PutMapping("/api/application-periods/{id}")
    public ApplicationPeriodResponse updatePeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationPeriodRequest request) {
        return service.update(caller, id, objectMapper.valueToTree(request));
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
