package at.ac.tgm.submit.formal;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.FormalApplicationSummaryResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FormalApplicationListController {
    private final FormalApplicationListService listService;

    public FormalApplicationListController(FormalApplicationListService listService) {
        this.listService = listService;
    }

    @GetMapping("/api/formal-applications")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List formal applications for teachers.",
            description = "Returns current proposal context. Search is a trimmed, case-insensitive substring "
                    + "match over project title, allocated member display names, application-period label, and "
                    + "school year. Search and the confirmed-supervisor filter are applied before pagination. "
                    + "Teachers may list, read, and review proposals regardless of supervision assignment.")
    public PageResponse<FormalApplicationSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Optional case-insensitive substring in project title, member display name, "
                    + "period label, or school year; surrounding whitespace is trimmed and blank is ignored.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Limit results to projects with a current confirmed supervisor assignment "
                    + "to the caller. Defaults to true.")
            @RequestParam(defaultValue = "true") boolean supervisedOnly,
            @Parameter(description = "Zero-based page index; defaults to 0.",
                    schema = @Schema(type = "integer", format = "int32", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; defaults to 20 and is normalized to the inclusive range 1–100.",
                    schema = @Schema(type = "integer", format = "int32", defaultValue = "20",
                            minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size) {
        return listService.list(caller, q, supervisedOnly, page, size);
    }
}
