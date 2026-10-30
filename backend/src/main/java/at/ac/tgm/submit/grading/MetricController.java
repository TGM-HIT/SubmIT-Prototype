package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.GradeDtos.MetricRequest;
import at.ac.tgm.submit.grading.GradeDtos.MetricResponse;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/projects/{projectId}/metrics")
@Tag(name = "Project metrics")
public class MetricController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @GetMapping
    @Operation(summary = "List metric observations for teachers and project members.")
    @GradingApiResponses.MetricsList
    public PageResponse<MetricResponse> listMetrics(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return metricService.list(caller, projectId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a metric observation validated against the configured metric definition.")
    @GradingApiResponses.ForbiddenWithoutSchema
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "The formal application is not approved, the rubric binding is ineffective, "
                    + "or a version transition lacks a required reason.",
            content = @io.swagger.v3.oas.annotations.media.Content(schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    public MetricResponse recordMetric(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody MetricRequest request) {
        return metricService.record(caller, projectId, request);
    }
}
