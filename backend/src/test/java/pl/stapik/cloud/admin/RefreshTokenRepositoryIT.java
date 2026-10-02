package pl.stapik.cloud.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.data.RefreshTokenData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RefreshTokenRepositoryIT extends AbstractIntegrationTest {

    private static final Duration LEEWAY = Duration.ofSeconds(30);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    private AdminUserData adminUser;

    @BeforeEach
    void setUp() {
        adminUser = createAdminUser("refresh-token-user");
    }

    @Test
    void shouldFindTokenByHash() {
        // given
        saveToken(adminUser, "hash-to-find", Instant.now().plus(Duration.ofDays(1)), null);

        // when
        boolean isFound = refreshTokenRepository.findByTokenHash("hash-to-find").isPresent();

        // then
        assertThat(isFound).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash("other-hash")).isEmpty();
    }

    @Test
    void shouldDeleteTokenByHash() {
        // given
        saveToken(adminUser, "hash-to-delete", Instant.now().plus(Duration.ofDays(1)), null);
        saveToken(adminUser, "hash-to-keep", Instant.now().plus(Duration.ofDays(1)), null);

        // when
        refreshTokenRepository.deleteByTokenHash("hash-to-delete");

        // then
        assertThat(refreshTokenRepository.findByTokenHash("hash-to-delete")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-to-keep")).isPresent();
    }

    @Test
    void shouldDeleteExpiredAndLongAgoUsedTokensButKeepActiveOnes() {
        // given
        Instant now = Instant.now();
        Instant futureExpiry = now.plus(Duration.ofDays(1));
        saveToken(adminUser, "expired", now.minusSeconds(1), null);
        saveToken(adminUser, "used-long-ago", futureExpiry, now.minus(LEEWAY).minusSeconds(60));
        saveToken(adminUser, "used-within-leeway", futureExpiry, now.minusSeconds(5));
        saveToken(adminUser, "unused", futureExpiry, null);

        // when
        int deletedCount = refreshTokenRepository.deleteStaleTokens(adminUser.getId(), now, now.minus(LEEWAY));

        // then
        assertThat(deletedCount).isEqualTo(2);
        List<String> remainingHashes = refreshTokenRepository.findAll().stream()
                .map(RefreshTokenData::getTokenHash)
                .toList();
        assertThat(remainingHashes).containsExactlyInAnyOrder("used-within-leeway", "unused");
    }

    @Test
    void shouldNotDeleteStaleTokensOfOtherAdminUsers() {
        // given
        Instant now = Instant.now();
        AdminUserData otherAdminUser = createAdminUser("other-refresh-token-user");
        saveToken(otherAdminUser, "other-user-expired", now.minusSeconds(1), null);

        // when
        int deletedCount = refreshTokenRepository.deleteStaleTokens(adminUser.getId(), now, now.minus(LEEWAY));

        // then
        assertThat(deletedCount).isZero();
        assertThat(refreshTokenRepository.findByTokenHash("other-user-expired")).isPresent();
    }

    private AdminUserData createAdminUser(String username) {
        AdminUserData user = new AdminUserData();
        user.setUsername(username);
        user.setPasswordHash("someHashedPassword");
        user.setRole("VIEWER");
        user.setCreatedAt(Instant.now());
        return adminUserRepository.saveAndFlush(user);
    }

    private void saveToken(AdminUserData owner, String tokenHash, Instant expiresAt, Instant usedAt) {
        refreshTokenRepository.saveAndFlush(RefreshTokenData.builder()
                .adminUserId(owner.getId())
                .tokenHash(tokenHash)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .build());
    }
}
