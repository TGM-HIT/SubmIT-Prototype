package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.GradeDtos.GroupGradeRequest;
import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Group grades")
public class GroupGradeController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private final GradeService gradeService;

    public GroupGradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PostMapping("/group-grades")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a teacher's assessment for a group-level rubric criterion.")
    @GradingApiResponses.ForbiddenWithoutSchema
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "The formal application is not approved, the rubric binding is ineffective, "
                    + "or a version transition lacks a required reason.",
            content = @io.swagger.v3.oas.annotations.media.Content(schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    public GradeResponse recordGroupGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody GroupGradeRequest request) {
        return gradeService.recordGroupGrade(caller, projectId, request);
    }
}
