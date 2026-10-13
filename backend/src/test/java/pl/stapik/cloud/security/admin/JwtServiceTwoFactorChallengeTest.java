package pl.stapik.cloud.security.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTwoFactorChallengeTest {

    private final JwtService jwtService = new JwtService("0123456789abcdef0123456789abcdef", "stapik-cloud", 15);

    @Test
    void shouldReturnAdminUserIdForValidChallenge() {
        UUID adminUserId = UUID.randomUUID();

        TwoFactorChallenge challenge = jwtService.generateTwoFactorChallenge(adminUserId, "owner");

        assertThat(jwtService.validateTwoFactorChallenge(challenge.token())).contains(adminUserId);
        assertThat(challenge.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldNotAcceptChallengeAsAccessToken() {
        TwoFactorChallenge challenge = jwtService.generateTwoFactorChallenge(UUID.randomUUID(), "owner");

        assertThat(jwtService.validate(challenge.token())).isEmpty();
    }

    @Test
    void shouldNotAcceptAccessTokenAsChallenge() {
        String accessToken = jwtService.generateToken(UUID.randomUUID(), "owner", "OWNER");

        assertThat(jwtService.validateTwoFactorChallenge(accessToken)).isEmpty();
    }

    @Test
    void shouldRejectGarbageChallenge() {
        assertThat(jwtService.validateTwoFactorChallenge("not-a-jwt")).isEmpty();
    }
}
