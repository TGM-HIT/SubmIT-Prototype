package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import ac.at.tgm.submit.web.ApiDtos.LanguageRequest;
import ac.at.tgm.submit.web.ApiDtos.UserResponse;
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
public class UserLanguageController {
    private final UserLanguageService languageService;

    public UserLanguageController(UserLanguageService languageService) {
        this.languageService = languageService;
    }

    @PatchMapping("/{id}/language")
    @Operation(summary = "Change the authenticated account's interface language.")
    @ApiResponse(responseCode = "200", description = "The account language was changed.",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "The account UUID or language request is invalid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication is required.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Only the account owner may change its language.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserResponse changeLanguage(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody LanguageRequest request) {
        return languageService.change(caller, id, request.language());
    }
}
