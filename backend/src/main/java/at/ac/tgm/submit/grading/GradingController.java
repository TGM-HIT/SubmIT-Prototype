package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.grading.GradeDtos.StudentGradeRequest;
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
@Tag(name = "Student grades")
public class GradingController {
    private static final String PROJECT_DESCRIPTION = "Project identifier.";
    private final StudentGradeService studentGrades;

    public GradingController(StudentGradeService studentGrades) {
        this.studentGrades = studentGrades;
    }

    @PostMapping("/student-grades")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a teacher's assessment of one student against one rubric criterion.")
    @GradingApiResponses.RecordStudentGrade
    public GradeResponse recordStudentGrade(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = PROJECT_DESCRIPTION) @PathVariable UUID projectId,
            @Valid @RequestBody StudentGradeRequest request) {
        return studentGrades.recordStudentGrade(caller, projectId, request);
    }
}
