package ac.at.tgm.submit.formal;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import ac.at.tgm.submit.web.ApiDtos.FormalApplicationRosterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Formal project applications")
public class FormalApplicationRosterController {
    private final FormalApplicationRosterService service;

    public FormalApplicationRosterController(FormalApplicationRosterService service) {
        this.service = service;
    }

    @GetMapping("/api/projects/{projectId}/formal-application-roster")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the finalized group roster for a student's Formal proposal.",
            description = "Returns only member account IDs and current display names to a STUDENT who belongs to "
                    + "the selected project's finalized allocated group. IDs are provided only to associate existing "
                    + "Formal proposal contribution fields; no caller-supplied student ID is accepted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The finalized group's private roster."),
        @ApiResponse(responseCode = "400", description = "The project ID must be a valid UUID.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Caller is not a student in this finalized group.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public FormalApplicationRosterResponse roster(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId) {
        return service.get(caller, projectId);
    }
}
