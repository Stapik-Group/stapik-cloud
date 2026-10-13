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
    void shouldReportLimitAndRemainingRequests() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(3);

        RateLimitDecision first = limiter.consume("key");
        RateLimitDecision second = limiter.consume("key");

        assertThat(first.limit()).isEqualTo(3);
        assertThat(first.remaining()).isEqualTo(2);
        assertThat(second.remaining()).isEqualTo(1);
        assertThat(second.resetAfterSeconds()).isPositive();
    }

    @Test
    void shouldReportNoRemainingRequestsWhenRejected() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1);
        limiter.consume("key");

        RateLimitDecision rejected = limiter.consume("key");

        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.limit()).isEqualTo(1);
    }

    @Test
    void shouldTrackKeysIndependently() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1);

        assertThat(limiter.consume("first").allowed()).isTrue();
        assertThat(limiter.consume("second").allowed()).isTrue();
        assertThat(limiter.consume("first").allowed()).isFalse();
    }
}
