package pl.stapik.cloud.security.admin;

import java.time.Instant;

public record TwoFactorChallenge(String token, Instant expiresAt) { }
