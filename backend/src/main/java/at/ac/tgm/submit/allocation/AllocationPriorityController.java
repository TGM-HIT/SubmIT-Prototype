package at.ac.tgm.submit.allocation;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.AllocationPriorityRequest;
import at.ac.tgm.submit.web.ApiDtos.AllocationPriorityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Allocation")
public class AllocationPriorityController {
    private final AllocationService service;

    public AllocationPriorityController(AllocationService service) {
        this.service = service;
    }

    @GetMapping("/api/application-periods/{periodId}/priority-projects")
    @Operation(summary = "Get priority projects for an application period.")
    @ApiResponse(responseCode = "200", description = "Current priority project IDs.",
            content = @Content(schema = @Schema(implementation = AllocationPriorityResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication is required.")
    @ApiResponse(responseCode = "403", description = "Only administrators may read priority projects.")
    @ApiResponse(responseCode = "404", description = "Application period was not found.")
    public AllocationPriorityResponse priorityProjects(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId) {
        return new AllocationPriorityResponse(service.priorityProjects(caller, periodId));
    }

    @PutMapping("/api/application-periods/{periodId}/priority-projects")
    @Operation(summary = "Replace priority projects for an application period.")
    @ApiResponse(responseCode = "200", description = "Saved priority project IDs.",
            content = @Content(schema = @Schema(implementation = AllocationPriorityResponse.class)))
    @ApiResponse(responseCode = "400", description = "Period ID must be a UUID; projectIds is required and IDs "
            + "must be unique and belong to the period's accepted catalogue.")
    @ApiResponse(responseCode = "401", description = "Authentication is required.")
    @ApiResponse(responseCode = "403", description = "Only administrators may change priority projects.")
    @ApiResponse(responseCode = "404", description = "Application period was not found.")
    @ApiResponse(responseCode = "409", description = "Finalized periods cannot be changed.")
    public AllocationPriorityResponse replacePriorityProjects(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @Valid @RequestBody AllocationPriorityRequest request) {
        return new AllocationPriorityResponse(service.replacePriorityProjects(caller, periodId, request.projectIds()));
    }
}
