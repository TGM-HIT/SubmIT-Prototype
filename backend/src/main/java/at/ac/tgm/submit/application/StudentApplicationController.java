package at.ac.tgm.submit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import at.ac.tgm.submit.web.ApiDtos.StudentApplicationRequest;
import at.ac.tgm.submit.web.ApiDtos.StudentApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "Submit ranked project preferences for an application period.")
    public StudentApplicationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @Valid @RequestBody StudentApplicationRequest request) {
        return service.createApplication(caller, periodId, objectMapper.valueToTree(request));
    }

    @GetMapping("/api/application-periods/{periodId}/applications/me")
    @Operation(summary = "Get the authenticated student's application for an application period.")
    public StudentApplicationResponse me(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId) {
        return service.myApplication(caller, periodId);
    }

    @GetMapping("/api/application-periods/{periodId}/applications")
    @Operation(summary = "List applications for an application period.")
    public PageResponse<StudentApplicationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return service.applications(caller, periodId, Math.max(page, 0), normalizeSize(size));
    }

    @PutMapping("/api/applications/{id}")
    @Operation(summary = "Update an application’s ranked project preferences.")
    public StudentApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody StudentApplicationRequest request) {
        return service.updateApplication(caller, id, objectMapper.valueToTree(request));
    }

    @DeleteMapping("/api/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw an application from an application period.")
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        service.withdrawApplication(caller, id);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
