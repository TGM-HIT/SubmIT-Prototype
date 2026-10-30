package at.ac.tgm.submit.user;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.UserResponse;
import at.ac.tgm.submit.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLanguageService {
    private final UserAccountRepository users;
    private final AuthService authService;

    public UserLanguageService(UserAccountRepository users, AuthService authService) {
        this.users = users;
        this.authService = authService;
    }

    @Transactional
    public UserResponse change(AuthenticatedUser caller, UUID id, AccountLanguage language) {
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (!caller.id().equals(id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only change your own account language.");
        }
        user.setLanguage(language);
        return authService.toResponse(user);
    }
}
