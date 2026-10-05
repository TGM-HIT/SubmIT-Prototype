package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.JwtService;
import com.flightlog.security.Role;
import com.flightlog.audit.AuditService;
import com.flightlog.web.ApiDtos.LoginRequest;
import com.flightlog.web.ApiDtos.RegisterRequest;
import com.flightlog.web.ApiDtos.TokenResponse;
import com.flightlog.web.ApiDtos.UserResponse;
import com.flightlog.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final long TOKEN_LIFETIME_SECONDS = 28_800L;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already registered.");
        }
        UserAccount user = users.save(new UserAccount(UUID.randomUUID(), username,
                passwordEncoder.encode(request.password()), Role.STUDENT));
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
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
