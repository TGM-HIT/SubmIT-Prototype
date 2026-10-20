package ac.at.tgm.submit.grading;

import ac.at.tgm.submit.grading.GradeDtos.GradeResponse;
import ac.at.tgm.submit.grading.GradeDtos.GradeRevisionRequest;
import ac.at.tgm.submit.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/projects/{projectId}/student-grades/{gradeId}/revisions")
@Tag(name = "Student grade revisions")
public class GradeRevisionController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";

    private final GradeService gradeService;

    public GradeRevisionController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a correction or reassessment; the prior entry is retained as superseded.")
    @ApiResponse(responseCode = "409", description = "The entry has already been superseded.")
    public GradeResponse reviseStudentGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Parameter(description = "Grade entry to revise.") @PathVariable UUID gradeId,
            @Valid @RequestBody GradeRevisionRequest request) {
        return gradeService.reviseStudentGrade(caller, projectId, gradeId, request);
    }
}
