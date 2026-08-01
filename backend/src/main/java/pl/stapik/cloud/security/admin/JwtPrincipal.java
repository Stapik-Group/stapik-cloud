package pl.stapik.cloud.security.admin;

import java.util.UUID;

public record JwtPrincipal(UUID adminUserId, String username, String role) {
}