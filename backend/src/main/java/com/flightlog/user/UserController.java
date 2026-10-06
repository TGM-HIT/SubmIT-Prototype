package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.web.ApiDtos.ApiErrorResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.RoleRequest;
import com.flightlog.web.ApiDtos.UserResponse;
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
    public PageResponse<UserResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.list(caller, Math.max(page, 0), normalizeSize(size));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change an account role. At least one administrator account must remain.")
    @ApiResponse(responseCode = "409", description = "The role change would remove the last administrator.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserResponse changeRole(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        Role role = request.role();
        return userService.changeRole(caller, id, role);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
