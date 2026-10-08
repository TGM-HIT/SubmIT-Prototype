package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.ApiErrorResponse;
import com.flightlog.web.ApiDtos.DisplayNameRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserNameController {
    private final UserService userService;

    public UserNameController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change an account name. Users may update their own name; administrators may update any account.")
    @ApiResponse(responseCode = "200", description = "The account name was changed.",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "The name is missing, blank after trimming, or longer than "
            + "120 characters after trimming.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Only the account owner or an administrator may change the name.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserResponse changeDisplayName(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody DisplayNameRequest request) {
        return userService.changeDisplayName(caller, id, request);
    }
}
