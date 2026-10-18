package com.flightlog.grading;

import com.flightlog.grading.FinalGradeDtos.FinalGradeHistory;
import com.flightlog.grading.FinalGradeDtos.FinalGradesRequest;
import com.flightlog.grading.FinalGradeDtos.FinalGradesResponse;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Final grades")
public class FinalGradeController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private static final String FORBIDDEN = "Caller lacks the required role or project access.";

    private final FinalGradeService finalGradeService;

    public FinalGradeController(FinalGradeService finalGradeService) {
        this.finalGradeService = finalGradeService;
    }

    @GetMapping("/final-grade")
    @Operation(summary = "Get the current final project grade, its basis, and history.")
    @ApiResponse(responseCode = "200", description = "Current final project grade and history.",
            content = @Content(schema = @Schema(implementation = FinalGradeHistory.class)))
    @ApiResponse(responseCode = "403", description = "A student caller is not a member of the project.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Project not found or no final grade has been recorded.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FinalGradeHistory getFinalGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId) {
        return finalGradeService.getProjectFinalGrade(caller, projectId);
    }

    @GetMapping("/students/{studentId}/final-grade")
    @Operation(summary = "Get a student's individual final grade, basis, and history (that student or teachers).")
    @ApiResponse(responseCode = "200", description = "Student's individual final grade and history.",
            content = @Content(schema = @Schema(implementation = FinalGradeHistory.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Project not found, student is not a project member, "
            + "or no final grade has been recorded.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FinalGradeHistory getStudentFinalGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Parameter(description = "Student identifier.") @PathVariable UUID studentId) {
        return finalGradeService.getStudentFinalGrade(caller, projectId, studentId);
    }

    @PutMapping("/final-grade")
    @Operation(summary = "Record or confirm project and individual final grades, by configured rule or with rationale.")
    @ApiResponse(responseCode = "200", description = "Recorded project and individual final grades.",
            content = @Content(schema = @Schema(implementation = FinalGradesResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request shape or student membership, grade, rationale, "
            + "or rule.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a teacher or administrator.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A configured rule lacks required inputs, the selected rubric "
            + "is ineffective, or a rubric-binding transition lacks a reason.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FinalGradesResponse putFinalGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody FinalGradesRequest request) {
        return finalGradeService.record(caller, projectId, request);
    }
}
