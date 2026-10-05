package com.flightlog.grading;

import com.flightlog.grading.RubricDtos.PublishRubricRequest;
import com.flightlog.grading.RubricDtos.RubricResponse;
import com.flightlog.grading.RubricDtos.RubricSummary;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading-rubrics")
@Tag(name = "Grading rubrics")
public class RubricController {
    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @GetMapping
    @Operation(summary = "List rubric versions and their effective periods.")
    @ApiResponse(responseCode = "200", description = "Paginated rubric version summaries.")
    public PageResponse<RubricSummary> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, at most 100.") @RequestParam(defaultValue = "20") int size) {
        return rubricService.list(caller, page, size);
    }

    @GetMapping("/active")
    @Operation(summary = "Get the rubric version effective today, with criteria, scales, metrics, and rules.")
    @ApiResponse(responseCode = "404", description = "No rubric version is currently effective.")
    public RubricResponse active(@AuthenticationPrincipal AuthenticatedUser caller) {
        return rubricService.active(caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific immutable rubric version.")
    @ApiResponse(responseCode = "404", description = "Rubric version not found.")
    public RubricResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Rubric version identifier.") @PathVariable UUID id) {
        return rubricService.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a new immutable rubric version (ADMIN only).")
    @ApiResponse(responseCode = "400", description = "Invalid rubric structure, scale, metric, or rule.")
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator.")
    @ApiResponse(responseCode = "409", description = "The rubric version label already exists.")
    public RubricResponse publish(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody PublishRubricRequest request) {
        return rubricService.publish(caller, request);
    }
}
