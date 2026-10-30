package at.ac.tgm.submit.user;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import at.ac.tgm.submit.web.ApiDtos.RoleRequest;
import at.ac.tgm.submit.web.ApiDtos.StudentLookupResponse;
import at.ac.tgm.submit.web.ApiDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List user accounts", description = "Optional role and q parameters filter by role and "
            + "case-insensitive username or display-name substring before pagination. Blank q is ignored; % and _ "
            + "are literal search characters.")
    @ApiResponse(responseCode = "200", description = "The filtered account page.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "The role filter is invalid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Only administrators may list user accounts.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<UserResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String q) {
        return userService.list(caller, Math.max(page, 0), normalizeSize(size), role, q);
    }

    @GetMapping("/students")
    @Operation(summary = "Search students by display name.")
    @ApiResponse(responseCode = "200", description = "Paginated display-name-only student results.",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Only students and administrators may use this lookup.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<StudentLookupResponse> lookupStudents(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return userService.lookupStudents(caller, Math.max(page, 0), normalizeSize(size), q);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change an account role. At least one administrator account must remain.")
    @ApiResponse(responseCode = "200", description = "The account role was changed.",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "409", description = "The role change would remove the last administrator.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserResponse changeRole(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return userService.changeRoles(caller, id, request.roles());
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
