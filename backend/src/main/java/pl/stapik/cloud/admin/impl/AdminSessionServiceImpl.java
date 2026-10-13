package pl.stapik.cloud.admin.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.admin.AdminSessionService;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.RefreshTokenData;
import pl.stapik.cloud.admin.dto.AdminSession;
import pl.stapik.cloud.admin.dto.SessionInfo;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.security.admin.ClientInfo;
import pl.stapik.cloud.security.admin.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminSessionServiceImpl implements AdminSessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final Duration refreshTokenLifetime;
    private final Duration reuseLeeway;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSessionServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            AdminUserRepository adminUserRepository,
            JwtService jwtService,
            @Value("${stapik-cloud.security.refresh-token.expiration-days}") long expirationDays,
            @Value("${stapik-cloud.security.refresh-token.reuse-leeway-seconds}") long reuseLeewaySeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.refreshTokenLifetime = Duration.ofDays(expirationDays);
        this.reuseLeeway = Duration.ofSeconds(reuseLeewaySeconds);
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_LOGIN_SUCCEEDED, actor = "#adminUser.username")
    @Override
    public AdminSession startSession(AdminUserData adminUser, ClientInfo client) {
        Instant now = Instant.now();
        deleteStaleTokens(adminUser, now);
        return issueSession(adminUser, UUID.randomUUID(), now, client, now);
    }

    @Transactional
    @Override
    public Optional<AdminSession> refreshSession(String refreshToken, ClientInfo client) {
        Instant now = Instant.now();

        Optional<RefreshTokenData> storedToken = refreshTokenRepository.findByTokenHash(hash(refreshToken));
        if (storedToken.isEmpty() || !isUsable(storedToken.get(), now)) {
            return Optional.empty();
        }

        RefreshTokenData currentToken = storedToken.get();
        if (currentToken.getUsedAt() == null) {
            currentToken.setUsedAt(now);
        }

        return adminUserRepository.findById(currentToken.getAdminUserId()).map(adminUser -> {
            deleteStaleTokens(adminUser, now);
            return issueSession(adminUser, currentToken.getSessionId(), currentToken.getSessionStartedAt(), client, now);
        });
    }

    @Transactional
    @Override
    public void endSession(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isActive(UUID sessionId) {
        return refreshTokenRepository.existsBySessionIdAndExpiresAtAfter(sessionId, Instant.now());
    }

    @Transactional(readOnly = true)
    @Override
    public List<SessionInfo> listSessions(UUID adminUserId, UUID currentSessionId) {
        return refreshTokenRepository.findByAdminUserIdAndUsedAtIsNullAndExpiresAtAfter(adminUserId, Instant.now()).stream()
                .collect(Collectors.toMap(
                        RefreshTokenData::getSessionId,
                        Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(RefreshTokenData::getCreatedAt))))
                .values().stream()
                .map(token -> toSessionInfo(token, currentSessionId))
                .sorted(Comparator.comparing(SessionInfo::lastActiveAt).reversed())
                .toList();
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_SESSION_REVOKED, details = "'Session ' + #sessionId + ' signed out'")
    @Override
    public void revokeSession(UUID adminUserId, UUID sessionId) {
        if (refreshTokenRepository.deleteByAdminUserIdAndSessionId(adminUserId, sessionId) == 0) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_SESSION_REVOKED, details = "'All other sessions signed out'")
    @Override
    public void revokeOtherSessions(UUID adminUserId, UUID currentSessionId) {
        refreshTokenRepository.deleteByAdminUserIdAndSessionIdNot(adminUserId, currentSessionId);
    }

    private SessionInfo toSessionInfo(RefreshTokenData token, UUID currentSessionId) {
        return new SessionInfo(
                token.getSessionId(),
                token.getSessionStartedAt(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getIpAddress(),
                token.getUserAgent(),
                token.getSessionId().equals(currentSessionId)
        );
    }

    private boolean isUsable(RefreshTokenData token, Instant now) {
        boolean isExpired = !token.getExpiresAt().isAfter(now);
        boolean isReusedAfterLeeway = token.getUsedAt() != null && token.getUsedAt().plus(reuseLeeway).isBefore(now);
        return !isExpired && !isReusedAfterLeeway;
    }

    private void deleteStaleTokens(AdminUserData adminUser, Instant now) {
        refreshTokenRepository.deleteStaleTokens(adminUser.getId(), now, now.minus(reuseLeeway));
    }

    private AdminSession issueSession(AdminUserData adminUser, UUID sessionId, Instant sessionStartedAt,
                                      ClientInfo client, Instant now) {
        String accessToken = jwtService.generateToken(
                adminUser.getId(), adminUser.getUsername(), adminUser.getRole(), sessionId);
        String rawRefreshToken = generateRawRefreshToken();
        Instant refreshTokenExpiresAt = now.plus(refreshTokenLifetime);

        refreshTokenRepository.save(RefreshTokenData.builder()
                .adminUserId(adminUser.getId())
                .sessionId(sessionId)
                .sessionStartedAt(sessionStartedAt)
                .ipAddress(client.ipAddress())
                .userAgent(client.userAgent())
                .tokenHash(hash(rawRefreshToken))
                .createdAt(now)
                .expiresAt(refreshTokenExpiresAt)
                .build());

        return new AdminSession(
                accessToken,
                jwtService.expirationOf(accessToken),
                rawRefreshToken,
                refreshTokenExpiresAt
        );
    }

    private String generateRawRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
