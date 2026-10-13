package pl.stapik.cloud.security.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, long limit, long remaining, Duration retryAfter, Duration resetAfter) {

    static RateLimitDecision permitted(long limit, long remaining, Duration resetAfter) {
        return new RateLimitDecision(true, limit, remaining, Duration.ZERO, resetAfter);
    }

    static RateLimitDecision rejected(long limit, Duration retryAfter, Duration resetAfter) {
        return new RateLimitDecision(false, limit, 0, retryAfter, resetAfter);
    }

    long resetAfterSeconds() {
        return wholeSeconds(resetAfter);
    }

    static long wholeSeconds(Duration duration) {
        return (duration.toMillis() + 999) / 1000;
    }
}
