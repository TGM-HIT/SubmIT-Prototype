package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.audit.AuditService;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.DisplayNameRequest;
import com.flightlog.web.ApiDtos.UserResponse;
import com.flightlog.web.ApiDtos.StudentLookupResponse;
import com.flightlog.web.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public PageResponse<UserResponse> list(AuthenticatedUser caller, int page, int size, Role role, String query) {
        requireRole(caller, Role.ADMIN);
        String search = normalizeSearch(query);
        Page<UserAccount> result = users.findByFilters(role, search, PageRequest.of(page, size, Sort.by("id")));
        return new PageResponse<>(result.map(authService::toResponse).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentLookupResponse> lookupStudents(AuthenticatedUser caller, int page, int size,
            String query) {
        requireRole(caller, Role.STUDENT);
        String search = query == null || query.isBlank() ? null
                : query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        Page<UserAccount> result = users.findStudentsByDisplayName(Role.STUDENT, search,
                PageRequest.of(page, size, Sort.by("id")));
        return new PageResponse<>(result.map(user -> new StudentLookupResponse(user.getId(), user.getDisplayName()))
                .getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private String normalizeSearch(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional
    public UserResponse changeRole(AuthenticatedUser caller, UUID id, Role role) {
        requireRole(caller, Role.ADMIN);
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN
                && users.findAllByRoleForUpdate(Role.ADMIN).size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "At least one administrator account must remain.");
        }
        user.setRole(role);
        audit.record(caller, "USER_ROLE_CHANGED", "user", id);
        return authService.toResponse(user);
    }

    @Transactional
    public UserResponse changeDisplayName(AuthenticatedUser caller, UUID id, DisplayNameRequest request) {
        if (!caller.id().equals(id) && caller.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only change your own account name.");
        }
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        user.setDisplayName(authService.normalizedName(request.displayName()));
        audit.record(caller, "USER_DISPLAY_NAME_CHANGED", "user", id);
        return authService.toResponse(user);
    }

    private void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This operation requires the " + role + " role.");
        }
    }
}
