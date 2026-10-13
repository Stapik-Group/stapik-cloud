package pl.stapik.cloud.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;

public class KeyedRateLimiter {

    private static final long MAX_TRACKED_KEYS = 100_000;
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    private final long requestsPerMinute;

    public KeyedRateLimiter(long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public RateLimitDecision consume(String key) {
        ConsumptionProbe probe = buckets.get(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);
        Duration resetAfter = Duration.ofNanos(probe.getNanosToWaitForReset());
        if (probe.isConsumed()) {
            return RateLimitDecision.permitted(requestsPerMinute, probe.getRemainingTokens(), resetAfter);
        }
        Duration retryAfter = Duration.ofNanos(probe.getNanosToWaitForRefill());
        return RateLimitDecision.rejected(requestsPerMinute, retryAfter, resetAfter);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
