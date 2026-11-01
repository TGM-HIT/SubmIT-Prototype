package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.AssessmentRubricDtos.AssessmentRubricResponse;
import at.ac.tgm.submit.grading.AssessmentRubricDtos.AssessmentRubricSelection;
import at.ac.tgm.submit.grading.AssessmentRubricDtos.GradingMember;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Project grading configuration")
public class AssessmentRubricController {
    private final AssessmentRubricService service;

    public AssessmentRubricController(AssessmentRubricService service) {
        this.service = service;
    }

    @GetMapping("/assessment-rubrics")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(operationId = "getProjectAssessmentRubrics",
            summary = "Read the independent routine and final rubric selections for an authorized project grader.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Independent project rubric selections.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Invalid project UUID.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller lacks project access or is not a project member.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project does not exist.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AssessmentRubricResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId) {
        return service.get(caller, projectId);
    }

    @PutMapping("/assessment-rubrics")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(operationId = "setProjectAssessmentRubrics",
            summary = "Select independent routine and final rubric versions for a project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Independent project rubric selections saved.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Invalid project UUID, malformed selection, or invalid reason.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller lacks project access or is not a teacher.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project or selected rubric version does not exist.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Selected rubric is ineffective, a used phase cannot be cleared, or a required "
                            + "transition reason is missing.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AssessmentRubricResponse put(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId, @Valid @RequestBody AssessmentRubricSelection request) {
        return service.put(caller, projectId, request);
    }

    @GetMapping("/grading-members")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(operationId = "listProjectGradingMembers",
            summary = "List the actual allocated project members for authorized grading staff.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Allocated project members with current display context.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Invalid project UUID.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not grading staff or lacks project access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project or allocated member account does not exist.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<GradingMember> members(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId) {
        return service.members(caller, projectId);
    }
}
