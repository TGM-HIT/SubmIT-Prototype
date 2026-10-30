package at.ac.tgm.submit.allocation;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.AllocationPinRequest;
import at.ac.tgm.submit.web.ApiDtos.AllocationPinResponse;
import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AllocationPinController {
    private final AllocationPinService service;

    public AllocationPinController(AllocationPinService service) {
        this.service = service;
    }

    @PutMapping("/api/application-periods/{periodId}/allocation-pins/{studentId}")
    @Operation(summary = "Pin a student to a project in an application period.")
    @ApiResponse(responseCode = "200", description = "Pin saved.")
    @ApiResponse(responseCode = "400",
            description = "Target is not a STUDENT account or project is outside the period catalogue.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Administrator role required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period or student not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Unranked project without acknowledgement, more than five project pins, or finalized period. "
                    + "Submitted applying units also reject conflicting member targets or effective capacity above five.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AllocationPinResponse put(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId,
            @PathVariable UUID studentId, @Valid @RequestBody AllocationPinRequest request) {
        return service.put(caller, periodId, studentId, request);
    }

    @DeleteMapping("/api/application-periods/{periodId}/allocation-pins/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a student's project pin from an application period.")
    @ApiResponse(responseCode = "204", description = "Pin removed.")
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Administrator role required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period or pin not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Period is finalized.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId,
            @PathVariable UUID studentId) {
        service.delete(caller, periodId, studentId);
    }

    @GetMapping("/api/application-periods/{periodId}/allocation-pins")
    @Operation(summary = "List project pins for an application period.")
    @ApiResponse(responseCode = "200", description = "Pins listed.")
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Teacher or administrator role required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<AllocationPinResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.list(periodId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new PageResponse<>(result.content(), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }
}
