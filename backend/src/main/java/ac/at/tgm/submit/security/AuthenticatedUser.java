package ac.at.tgm.submit.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String username, Role role) {
}
