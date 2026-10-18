package com.flightlog.grading;

import com.flightlog.grading.GradeDtos.GradeResponse;
import com.flightlog.grading.GradeDtos.GroupGradeRequest;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.ApiErrorResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Group grades")
public class GroupGradeController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private static final String PAGE_DESCRIPTION = "Zero-based page index.";
    private static final String SIZE_DESCRIPTION = "Page size, at most 100.";
    private static final String FORBIDDEN = "Caller lacks the required role or project access.";

    private final GradeService gradeService;

    public GroupGradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping("/group-grades")
    @Operation(summary = "List group-level assessment history for teachers and group members.")
    @ApiResponse(responseCode = "200", description = "Paginated group-level assessment history.",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<GradeResponse> listGroupGrades(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Parameter(description = PAGE_DESCRIPTION) @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION) @RequestParam(defaultValue = "20") int size) {
        return gradeService.listGroupGrades(caller, projectId, page, size);
    }

    @PostMapping("/group-grades")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a teacher's assessment for a group-level rubric criterion.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN)
    public GradeResponse recordGroupGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody GroupGradeRequest request) {
        return gradeService.recordGroupGrade(caller, projectId, request);
    }
}
