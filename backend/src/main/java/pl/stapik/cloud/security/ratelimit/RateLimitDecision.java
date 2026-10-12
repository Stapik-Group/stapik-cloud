package pl.stapik.cloud.security.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    static RateLimitDecision permitted() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    static RateLimitDecision rejected(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
