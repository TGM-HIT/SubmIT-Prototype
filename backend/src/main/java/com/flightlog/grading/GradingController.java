package com.flightlog.grading;

import com.flightlog.grading.GradeDtos.GradeResponse;
import com.flightlog.grading.GradeDtos.StudentGradeRequest;
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
@Tag(name = "Student grades")
public class GradingController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private static final String PAGE_DESCRIPTION = "Zero-based page index.";
    private static final String SIZE_DESCRIPTION = "Page size, at most 100.";
    private static final String FORBIDDEN = "Caller lacks the required role or project access.";

    private final GradeService gradeService;

    public GradingController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping("/student-grades")
    @Operation(summary = "List individual grade history; students see only their own grades.")
    @ApiResponse(responseCode = "200", description = "Paginated individual grade history.",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<GradeResponse> listStudentGrades(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Parameter(description = "Optional student filter.") @RequestParam(required = false) String studentId,
            @Parameter(description = PAGE_DESCRIPTION) @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION) @RequestParam(defaultValue = "20") int size) {
        return gradeService.listStudentGrades(caller, projectId, studentId, page, size);
    }

    @PostMapping("/student-grades")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a teacher's assessment of one student against one rubric criterion.")
    @ApiResponse(responseCode = "400", description = "Grade outside the criterion scale or invalid input.")
    @ApiResponse(responseCode = "403", description = FORBIDDEN)
    @ApiResponse(responseCode = "409", description = "Rubric version differs from the project's bound version.")
    public GradeResponse recordStudentGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody StudentGradeRequest request) {
        return gradeService.recordStudentGrade(caller, projectId, request);
    }
}
