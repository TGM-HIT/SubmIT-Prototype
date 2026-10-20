package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.JwtService;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.web.ApiDtos.LoginRequest;
import ac.at.tgm.submit.web.ApiDtos.RegisterRequest;
import ac.at.tgm.submit.web.ApiDtos.TokenResponse;
import ac.at.tgm.submit.web.ApiDtos.UserResponse;
import ac.at.tgm.submit.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final long TOKEN_LIFETIME_SECONDS = 28_800L;
    private static final int MAX_DISPLAY_NAME_LENGTH = 120;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final DefaultStudentClassService defaultStudentClass;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            AuditService audit, DefaultStudentClassService defaultStudentClass) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.defaultStudentClass = defaultStudentClass;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String displayName = normalizedName(request.displayName());
        if (users.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already registered.");
        }
        UserAccount user = new UserAccount(UUID.randomUUID(), username, displayName,
                passwordEncoder.encode(request.password()), Role.STUDENT);
        user.setStudentClass(defaultStudentClass.getOrCreate());
        users.save(user);
        audit.record(new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole()),
                "USER_REGISTERED", "user", user.getId());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        UserAccount user = users.findByUsername(request.username().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        return new TokenResponse(jwtService.issueToken(principal), "Bearer", TOKEN_LIFETIME_SECONDS,
                toResponse(user));
    }

    public UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), null, null);
    }

    public String normalizedName(String displayName) {
        String normalized = displayName.strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > MAX_DISPLAY_NAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Account name must contain 1 to 120 characters after trimming.");
        }
        return normalized;
    }
}
