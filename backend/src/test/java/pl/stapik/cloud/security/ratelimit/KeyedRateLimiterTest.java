package pl.stapik.cloud.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedRateLimiterTest {

    @Test
    void shouldRejectRequestsOverTheLimit() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(2);

        assertThat(limiter.consume("key").allowed()).isTrue();
        assertThat(limiter.consume("key").allowed()).isTrue();

        RateLimitDecision decision = limiter.consume("key");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isPositive();
    }

    @Test
    void shouldTrackKeysIndependently() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1);

        assertThat(limiter.consume("first").allowed()).isTrue();
        assertThat(limiter.consume("second").allowed()).isTrue();
        assertThat(limiter.consume("first").allowed()).isFalse();
    }
}
