package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.grading.GradeDtos.StudentGradeQuery;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Separate read endpoint for exact phase/version filtering without changing write-route behavior. */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Student grades")
public class StudentGradeHistoryController {
    private final GradeHistoryReader historyReader;

    public StudentGradeHistoryController(GradeHistoryReader historyReader) {
        this.historyReader = historyReader;
    }

    @GetMapping("/student-grades")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary = "List project individual grade history; teachers may filter by studentId, while students "
            + "may read only their own grades.")
    @GradingApiResponses.StudentGradesList
    public PageResponse<GradeResponse> listStudentGrades(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Project identifier.") @PathVariable UUID projectId,
            @ParameterObject @ModelAttribute StudentGradeQuery query) {
        return historyReader.read(caller, projectId, query);
    }
}
