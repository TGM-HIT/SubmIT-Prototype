package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.DisplayNameRequest;
import ac.at.tgm.submit.web.ApiDtos.UserResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentDetailsResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentLookupResponse;
import ac.at.tgm.submit.web.ApiException;
import java.util.Locale;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserAccountRepository users;
    private final AuthService authService;
    private final AuditService audit;
    private final StudentDetailsService studentDetails;
    private final DefaultStudentClassService defaultStudentClass;

    public UserService(UserAccountRepository users, AuthService authService, AuditService audit,
            StudentDetailsService studentDetails, DefaultStudentClassService defaultStudentClass) {
        this.users = users;
        this.authService = authService;
        this.audit = audit;
        this.studentDetails = studentDetails;
        this.defaultStudentClass = defaultStudentClass;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(AuthenticatedUser caller, int page, int size, Role role, String query) {
        requireRole(caller, Role.ADMIN);
        String search = normalizeSearch(query);
        Page<UserAccount> result = users.findByFilters(role, search, PageRequest.of(page, size, Sort.by("id")));
        return new PageResponse<>(result.map(user -> {
            UserResponse response = authService.toResponse(user);
            if (!user.hasRole(Role.STUDENT)) {
                return response;
            }
            StudentDetailsResponse details = studentDetails.response(user);
            return new UserResponse(response.id(), response.username(), response.displayName(), response.roles(),
                    response.language(), details.className(), details.electiveSubjectNames());
        }).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ROLE_STUDENT','ROLE_ADMIN')")
    public PageResponse<StudentLookupResponse> lookupStudents(AuthenticatedUser caller, int page, int size,
            String query) {
        if (!caller.hasRole(Role.STUDENT) && !caller.hasRole(Role.ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("Student lookup is restricted.");
        }
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
    public UserResponse changeRoles(AuthenticatedUser caller, UUID id, List<Role> roles) {
        requireRole(caller, Role.ADMIN);
        validateRoleSet(roles);
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        assignDefaultStudentClass(user, roles);
        ensureAdministratorRemains(user, roles);
        user.setRoles(roles);
        audit.record(caller, "USER_ROLE_CHANGED", "user", id);
        return authService.toResponse(user);
    }

    private void validateRoleSet(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw invalidRoleSet();
        }
        if (roles.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidRoleSet();
        }
        if (roles.size() != new HashSet<>(roles).size()) {
            throw invalidRoleSet();
        }
        if (roles.contains(Role.STUDENT) && roles.size() > 1) {
            throw invalidRoleSet();
        }
    }

    private ApiException invalidRoleSet() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid account role set.");
    }

    private void assignDefaultStudentClass(UserAccount user, List<Role> roles) {
        if (roles.contains(Role.STUDENT) && !user.hasRole(Role.STUDENT) && user.getStudentClass() == null) {
            user.setStudentClass(defaultStudentClass.getOrCreate());
        }
    }

    private void ensureAdministratorRemains(UserAccount user, List<Role> roles) {
        if (user.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN)
                && users.findAllByRoleForUpdate(Role.ADMIN).size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "At least one administrator account must remain.");
        }
    }

    @Transactional
    public UserResponse changeDisplayName(AuthenticatedUser caller, UUID id, DisplayNameRequest request) {
        if (!caller.id().equals(id) && !caller.hasRole(Role.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only change your own account name.");
        }
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        user.setDisplayName(authService.normalizedName(request.displayName()));
        audit.record(caller, "USER_DISPLAY_NAME_CHANGED", "user", id);
        return authService.toResponse(user);
    }

    private void requireRole(AuthenticatedUser caller, Role role) {
        if (!caller.hasRole(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This operation requires the " + role + " role.");
        }
    }
}
