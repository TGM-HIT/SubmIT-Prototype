package com.flightlog.formal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.FormalApplicationRequest;
import com.flightlog.web.ApiDtos.FormalApplicationResponse;
import com.flightlog.web.ApiDtos.ReviewRequest;
import com.flightlog.web.ApiDtos.FormalReviewResponse;
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
@Tag(name = "Formal project applications")
public class FormalApplicationController {
    private final FormalApplicationService service;
    private final ObjectMapper objectMapper;

    public FormalApplicationController(FormalApplicationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/projects/{projectId}/formal-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft formal application for an allocated group.",
            description = "Text limits count Java UTF-16 code units. For each allocated group member, provide exactly one "
                    + "individual topic, investigation concern, and planned result. A group has 3 to 5 members; each "
                    + "individual topic is at most 200 characters, and investigation-concern text and planned-result "
                    + "text are each limited to 2,400 characters combined across all group members.")
    public FormalApplicationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId,
            @Valid @RequestBody FormalApplicationRequest request) {
        return service.create(caller, projectId, objectMapper.valueToTree(request));
    }

    @GetMapping("/api/projects/{projectId}/formal-applications")
    @Operation(summary = "Get the formal application for a project.")
    public FormalApplicationResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.get(caller, projectId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @PutMapping("/api/formal-applications/{id}")
    @Operation(summary = "Update a draft or returned formal application.")
    public FormalApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody FormalApplicationRequest request) {
        return service.update(caller, id, objectMapper.valueToTree(request));
    }

    @PostMapping("/api/formal-applications/{id}/submit")
    @Operation(summary = "Submit a formal application for teacher review.")
    public FormalApplicationResponse submit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return service.submit(caller, id);
    }

    @PostMapping("/api/formal-applications/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review a formal application and record its decision and feedback.")
    public FormalReviewResponse review(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        return service.review(caller, id, objectMapper.valueToTree(request));
    }
}
