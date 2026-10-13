package pl.stapik.cloud.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionInfo(
        UUID id,
        Instant startedAt,
        Instant lastActiveAt,
        Instant expiresAt,
        String ipAddress,
        String userAgent,
        boolean current
) { }
