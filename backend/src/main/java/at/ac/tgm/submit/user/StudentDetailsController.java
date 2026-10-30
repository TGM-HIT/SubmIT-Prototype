package at.ac.tgm.submit.user;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.CatalogueEntryRequest;
import at.ac.tgm.submit.web.ApiDtos.CatalogueEntryResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import at.ac.tgm.submit.web.ApiDtos.StudentDetailsRequest;
import at.ac.tgm.submit.web.ApiDtos.StudentDetailsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentDetailsController {
    private static final int MAX_PAGE_SIZE = 100;
    private final StudentDetailsService details;

    public StudentDetailsController(StudentDetailsService details) {
        this.details = details;
    }

    @GetMapping("/api/student-classes")
    @Operation(summary = "List the shared student-class catalogue.")
    public PageResponse<CatalogueEntryResponse> classes(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return details.classes(caller, Math.max(page, 0), normalizeSize(size));
    }

    @PatchMapping("/api/student-classes/{id}")
    @Operation(summary = "Rename a shared student-class catalogue entry.")
    public CatalogueEntryResponse renameClass(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody CatalogueEntryRequest request) {
        return details.renameClass(caller, id, request);
    }

    @GetMapping("/api/elective-subjects")
    @Operation(summary = "List the shared elective-subject catalogue.")
    public PageResponse<CatalogueEntryResponse> subjects(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return details.subjects(caller, Math.max(page, 0), normalizeSize(size));
    }

    @PatchMapping("/api/elective-subjects/{id}")
    @Operation(summary = "Rename a shared elective-subject catalogue entry.")
    public CatalogueEntryResponse renameSubject(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody CatalogueEntryRequest request) {
        return details.renameSubject(caller, id, request);
    }

    @GetMapping("/api/users/{id}/student-details")
    @Operation(summary = "Get the student's current class and elective-subject assignments.")
    public StudentDetailsResponse details(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        return details.details(caller, id);
    }

    @PutMapping("/api/users/{id}/student-details")
    @Operation(summary = "Assign a student's class and elective subjects.")
    public StudentDetailsResponse assign(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody StudentDetailsRequest request) {
        return details.assign(caller, id, request);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
