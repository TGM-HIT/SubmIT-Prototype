package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only group assessment history endpoint. */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Group grades")
public class GroupGradeHistoryController {
    private static final String PAGE_DESCRIPTION = "Zero-based page index.";
    private static final String SIZE_DESCRIPTION =
            "Page size; values are normalized to the inclusive range 1–100.";

    private final GradeHistoryReader historyReader;

    public GroupGradeHistoryController(GradeHistoryReader historyReader) {
        this.historyReader = historyReader;
    }

    @GetMapping("/group-grades")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary = "List group-level assessment history for teachers and group members.")
    @GradingApiResponses.GroupGradesList
    public PageResponse<GradeResponse> listGroupGrades(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Project identifier.") @PathVariable UUID projectId,
            @Parameter(description = PAGE_DESCRIPTION) @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION) @RequestParam(defaultValue = "20") int size) {
        return historyReader.readGroup(caller, projectId, page, size);
    }
}
