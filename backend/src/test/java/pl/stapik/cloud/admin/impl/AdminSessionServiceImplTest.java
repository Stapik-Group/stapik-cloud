package pl.stapik.cloud.admin.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.RefreshTokenData;
import pl.stapik.cloud.admin.dto.AdminSession;
import pl.stapik.cloud.admin.dto.SessionInfo;
import pl.stapik.cloud.security.admin.ClientInfo;
import pl.stapik.cloud.security.admin.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSessionServiceImplTest {

    private static final long REFRESH_EXPIRATION_DAYS = 7;
    private static final long REUSE_LEEWAY_SECONDS = 30;
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "test-agent");
    private static final String ACCESS_TOKEN = "access-token";
    private static final Instant ACCESS_TOKEN_EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private JwtService jwtService;

    private AdminSessionServiceImpl adminSessionService;
    private AdminUserData adminUser;

    @BeforeEach
    void setUp() {
        adminSessionService = new AdminSessionServiceImpl(
                refreshTokenRepository, adminUserRepository, jwtService, REFRESH_EXPIRATION_DAYS, REUSE_LEEWAY_SECONDS);
        adminUser = AdminUserData.builder()
                .id(UUID.randomUUID())
                .username("adminUser")
                .role("OWNER")
                .build();
    }

    @Test
    void shouldStartSessionWithAccessTokenAndHashedRefreshToken() {
        // given
        givenAccessTokenIsIssued();

        // when
        AdminSession session = adminSessionService.startSession(adminUser, CLIENT);

        // then
        assertThat(session.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(session.accessTokenExpiresAt()).isEqualTo(ACCESS_TOKEN_EXPIRES_AT);
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.refreshTokenExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofDays(REFRESH_EXPIRATION_DAYS)).minusSeconds(5),
                        Instant.now().plus(Duration.ofDays(REFRESH_EXPIRATION_DAYS)).plusSeconds(5));

        RefreshTokenData savedToken = captureSavedToken();
        assertThat(savedToken.getAdminUserId()).isEqualTo(adminUser.getId());
        assertThat(savedToken.getTokenHash()).isEqualTo(sha256Hex(session.refreshToken()));
        assertThat(savedToken.getTokenHash()).isNotEqualTo(session.refreshToken());
        assertThat(savedToken.getUsedAt()).isNull();
        verify(refreshTokenRepository).deleteStaleTokens(eq(adminUser.getId()), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldIssueDifferentRefreshTokensOnEverySession() {
        // given
        givenAccessTokenIsIssued();

        // when
        AdminSession firstSession = adminSessionService.startSession(adminUser, CLIENT);
        AdminSession secondSession = adminSessionService.startSession(adminUser, CLIENT);

        // then
        assertThat(firstSession.refreshToken()).isNotEqualTo(secondSession.refreshToken());
    }

    @Test
    void shouldRefreshSessionAndMarkPresentedTokenAsUsed() {
        // given
        String presentedToken = "presented-refresh-token";
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().plus(Duration.ofDays(1)), null);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));
        given(adminUserRepository.findById(adminUser.getId())).willReturn(Optional.of(adminUser));
        givenAccessTokenIsIssued();

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        assertThat(session).isPresent();
        assertThat(session.get().refreshToken()).isNotEqualTo(presentedToken);
        assertThat(storedToken.getUsedAt()).isNotNull();
        assertThat(captureSavedToken().getTokenHash()).isEqualTo(sha256Hex(session.get().refreshToken()));
    }

    @Test
    void shouldAcceptTokenReusedWithinLeewayWithoutChangingFirstUseTime() {
        // given
        String presentedToken = "presented-refresh-token";
        Instant firstUsedAt = Instant.now().minusSeconds(5);
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().plus(Duration.ofDays(1)), firstUsedAt);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));
        given(adminUserRepository.findById(adminUser.getId())).willReturn(Optional.of(adminUser));
        givenAccessTokenIsIssued();

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        assertThat(session).isPresent();
        assertThat(storedToken.getUsedAt()).isEqualTo(firstUsedAt);
    }

    @Test
    void shouldRejectTokenReusedAfterLeeway() {
        // given
        String presentedToken = "presented-refresh-token";
        Instant firstUsedAt = Instant.now().minusSeconds(REUSE_LEEWAY_SECONDS + 60);
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().plus(Duration.ofDays(1)), firstUsedAt);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        assertThat(session).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectExpiredToken() {
        // given
        String presentedToken = "presented-refresh-token";
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().minusSeconds(1), null);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        assertThat(session).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownToken() {
        // given
        given(refreshTokenRepository.findByTokenHash(sha256Hex("unknown"))).willReturn(Optional.empty());

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession("unknown", CLIENT);

        // then
        assertThat(session).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectTokenWhoseAdminUserNoLongerExists() {
        // given
        String presentedToken = "presented-refresh-token";
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().plus(Duration.ofDays(1)), null);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));
        given(adminUserRepository.findById(adminUser.getId())).willReturn(Optional.empty());

        // when
        Optional<AdminSession> session = adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        assertThat(session).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldKeepSessionIdentityAcrossRefresh() {
        // given
        String presentedToken = "presented-refresh-token";
        RefreshTokenData storedToken = storedToken(presentedToken, Instant.now().plus(Duration.ofDays(1)), null);
        given(refreshTokenRepository.findByTokenHash(sha256Hex(presentedToken))).willReturn(Optional.of(storedToken));
        given(adminUserRepository.findById(adminUser.getId())).willReturn(Optional.of(adminUser));
        givenAccessTokenIsIssued();

        // when
        adminSessionService.refreshSession(presentedToken, CLIENT);

        // then
        RefreshTokenData rotatedToken = captureSavedToken();
        assertThat(rotatedToken.getSessionId()).isEqualTo(storedToken.getSessionId());
        assertThat(rotatedToken.getSessionStartedAt()).isEqualTo(storedToken.getSessionStartedAt());
        assertThat(rotatedToken.getIpAddress()).isEqualTo(CLIENT.ipAddress());
        assertThat(rotatedToken.getUserAgent()).isEqualTo(CLIENT.userAgent());
    }

    @Test
    void shouldListOneSessionPerSessionIdMarkingTheCurrentOne() {
        // given
        RefreshTokenData currentSessionOlder = storedToken("a", Instant.now().plus(Duration.ofDays(1)), null);
        RefreshTokenData currentSessionNewer = storedToken("b", Instant.now().plus(Duration.ofDays(1)), null);
        currentSessionNewer.setSessionId(currentSessionOlder.getSessionId());
        currentSessionNewer.setCreatedAt(currentSessionOlder.getCreatedAt().plusSeconds(30));
        RefreshTokenData otherSession = storedToken("c", Instant.now().plus(Duration.ofDays(1)), null);
        otherSession.setCreatedAt(currentSessionOlder.getCreatedAt().minusSeconds(30));
        given(refreshTokenRepository.findByAdminUserIdAndUsedAtIsNullAndExpiresAtAfter(eq(adminUser.getId()), any(Instant.class)))
                .willReturn(List.of(currentSessionOlder, currentSessionNewer, otherSession));

        // when
        List<SessionInfo> sessions = adminSessionService.listSessions(adminUser.getId(), currentSessionOlder.getSessionId());

        // then
        assertThat(sessions).extracting(SessionInfo::id)
                .containsExactly(currentSessionOlder.getSessionId(), otherSession.getSessionId());
        assertThat(sessions).extracting(SessionInfo::current).containsExactly(true, false);
        assertThat(sessions.getFirst().lastActiveAt()).isEqualTo(currentSessionNewer.getCreatedAt());
    }

    @Test
    void shouldRevokeSessionOwnedByAdmin() {
        // given
        UUID sessionId = UUID.randomUUID();
        given(refreshTokenRepository.deleteByAdminUserIdAndSessionId(adminUser.getId(), sessionId)).willReturn(2L);

        // when & then
        adminSessionService.revokeSession(adminUser.getId(), sessionId);
        verify(refreshTokenRepository).deleteByAdminUserIdAndSessionId(adminUser.getId(), sessionId);
    }

    @Test
    void shouldFailRevokingUnknownSession() {
        // given
        UUID sessionId = UUID.randomUUID();
        given(refreshTokenRepository.deleteByAdminUserIdAndSessionId(adminUser.getId(), sessionId)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> adminSessionService.revokeSession(adminUser.getId(), sessionId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldRevokeAllSessionsExceptTheCurrentOne() {
        // given
        UUID currentSessionId = UUID.randomUUID();

        // when
        adminSessionService.revokeOtherSessions(adminUser.getId(), currentSessionId);

        // then
        verify(refreshTokenRepository).deleteByAdminUserIdAndSessionIdNot(adminUser.getId(), currentSessionId);
    }

    @Test
    void shouldDeleteTokenByHashWhenEndingSession() {
        // when
        adminSessionService.endSession("presented-refresh-token");

        // then
        verify(refreshTokenRepository).deleteByTokenHash(sha256Hex("presented-refresh-token"));
    }

    private void givenAccessTokenIsIssued() {
        given(jwtService.generateToken(eq(adminUser.getId()), eq(adminUser.getUsername()), eq(adminUser.getRole()), any(UUID.class)))
                .willReturn(ACCESS_TOKEN);
        given(jwtService.expirationOf(ACCESS_TOKEN)).willReturn(ACCESS_TOKEN_EXPIRES_AT);
    }

    private RefreshTokenData captureSavedToken() {
        ArgumentCaptor<RefreshTokenData> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenData.class);
        verify(refreshTokenRepository, atLeastOnce()).save(tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    private RefreshTokenData storedToken(String rawToken, Instant expiresAt, Instant usedAt) {
        return RefreshTokenData.builder()
                .id(UUID.randomUUID())
                .adminUserId(adminUser.getId())
                .sessionId(UUID.randomUUID())
                .sessionStartedAt(Instant.now().minusSeconds(3600))
                .tokenHash(sha256Hex(rawToken))
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .build();
    }

    private static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
