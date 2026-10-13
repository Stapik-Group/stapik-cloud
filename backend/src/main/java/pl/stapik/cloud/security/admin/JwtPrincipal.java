package pl.stapik.cloud.security.admin;

import java.security.Principal;
import java.util.UUID;

public record JwtPrincipal(UUID adminUserId, String username, String role, UUID sessionId) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}
