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
import pl.stapik.cloud.security.admin.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

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
    @Override
    public AdminSession startSession(AdminUserData adminUser) {
        Instant now = Instant.now();
        deleteStaleTokens(adminUser, now);
        return issueSession(adminUser, now);
    }

    @Transactional
    @Override
    public Optional<AdminSession> refreshSession(String refreshToken) {
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
            return issueSession(adminUser, now);
        });
    }

    @Transactional
    @Override
    public void endSession(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    private boolean isUsable(RefreshTokenData token, Instant now) {
        boolean isExpired = !token.getExpiresAt().isAfter(now);
        boolean isReusedAfterLeeway = token.getUsedAt() != null && token.getUsedAt().plus(reuseLeeway).isBefore(now);
        return !isExpired && !isReusedAfterLeeway;
    }

    private void deleteStaleTokens(AdminUserData adminUser, Instant now) {
        refreshTokenRepository.deleteStaleTokens(adminUser.getId(), now, now.minus(reuseLeeway));
    }

    private AdminSession issueSession(AdminUserData adminUser, Instant now) {
        String accessToken = jwtService.generateToken(adminUser.getId(), adminUser.getUsername(), adminUser.getRole());
        String rawRefreshToken = generateRawRefreshToken();
        Instant refreshTokenExpiresAt = now.plus(refreshTokenLifetime);

        refreshTokenRepository.save(RefreshTokenData.builder()
                .adminUserId(adminUser.getId())
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
