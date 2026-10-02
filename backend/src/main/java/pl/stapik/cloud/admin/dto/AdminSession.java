package pl.stapik.cloud.admin.dto;

import java.time.Instant;

public record AdminSession(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) { }
