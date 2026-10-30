package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import at.ac.tgm.submit.grading.RubricDtos.RubricResponse;
import at.ac.tgm.submit.grading.RubricDtos.RubricSummary;
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
@RequestMapping("/api/grading-rubrics")
@Tag(name = "Grading rubrics")
public class RubricController {
    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @GetMapping
    @Operation(summary = "List rubric versions and their effective periods.")
    @GradingApiResponses.RubricList
    public PageResponse<RubricSummary> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return rubricService.list(caller, page, size);
    }

    @GetMapping("/active")
    @Operation(summary = "Get the rubric version effective today, with criteria, scales, metrics, and rules.")
    @GradingApiResponses.ActiveRubric
    public RubricResponse active(@AuthenticationPrincipal AuthenticatedUser caller) {
        return rubricService.active(caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific immutable rubric version.")
    @GradingApiResponses.SpecificRubric
    public RubricResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Rubric version identifier.") @PathVariable UUID id) {
        return rubricService.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a new immutable rubric version (ADMIN only).")
    @GradingApiResponses.PublishRubric
    public RubricResponse publish(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody PublishRubricRequest request) {
        return rubricService.publish(caller, request);
    }
}
