package ac.at.tgm.submit.security;

import java.util.UUID;
import java.util.List;

public record AuthenticatedUser(UUID id, String username, List<Role> roles) {
    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }

    public AuthenticatedUser(UUID id, String username, Role role) {
        this(id, username, List.of(role));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public Role role() {
        return roles.get(0);
    }
}
