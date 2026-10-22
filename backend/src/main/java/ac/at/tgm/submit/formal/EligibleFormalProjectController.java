package ac.at.tgm.submit.formal;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import ac.at.tgm.submit.web.ApiDtos.EligibleFormalProjectResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Formal project applications")
public class EligibleFormalProjectController {
    private final EligibleFormalProjectService service;

    public EligibleFormalProjectController(EligibleFormalProjectService service) {
        this.service = service;
    }

    @GetMapping("/api/formal-applications/eligible-projects")
    @Operation(summary = "List finalized projects eligible for the authenticated student's Formal proposal.",
            description = "Returns only projects in persisted finalized allocation groups containing the caller. "
                    + "The response excludes group members and other allocation data. Pagination uses zero-based "
                    + "page (default 0) and size (default 20, normalized to 1–100).")
    @ApiResponse(responseCode = "200", description = "Eligible projects listed.")
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Student role required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<EligibleFormalProjectResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Zero-based page index; defaults to 0.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; defaults to 20 and is normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return service.list(caller, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }
}
