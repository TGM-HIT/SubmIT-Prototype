package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.audit.AuditService;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.UserResponse;
import com.flightlog.web.ApiException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserAccountRepository users;
    private final AuthService authService;
    private final AuditService audit;

    public UserService(UserAccountRepository users, AuthService authService, AuditService audit) {
        this.users = users;
        this.authService = authService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(AuthenticatedUser caller, int page, int size) {
        requireRole(caller, Role.ADMIN);
        Page<UserAccount> result = users.findAll(PageRequest.of(page, size));
        return new PageResponse<>(result.map(authService::toResponse).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public UserResponse changeRole(AuthenticatedUser caller, UUID id, Role role) {
        requireRole(caller, Role.ADMIN);
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        user.setRole(role);
        audit.record(caller, "USER_ROLE_CHANGED", "user", id);
        return authService.toResponse(user);
    }

    private void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This operation requires the " + role + " role.");
        }
    }
}
