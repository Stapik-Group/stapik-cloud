package pl.stapik.cloud.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class LoginAttemptTracker {

    private static final long MAX_TRACKED_USERNAMES = 100_000;

    private final Cache<String, Integer> failedAttempts;
    private final Policy.FixedExpiration<String, Integer> expiration;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    @Autowired
    public LoginAttemptTracker(RateLimitProperties properties) {
        this(properties.login().maxFailedAttempts(), properties.login().lockoutDuration(), Ticker.systemTicker());
    }

    LoginAttemptTracker(int maxFailedAttempts, Duration lockoutDuration, Ticker ticker) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = lockoutDuration;
        this.failedAttempts = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_USERNAMES)
                .expireAfterWrite(lockoutDuration)
                .ticker(ticker)
                .build();
        this.expiration = failedAttempts.policy().expireAfterWrite().orElseThrow();
    }

    public void checkNotLocked(String username) {
        String key = normalize(username);
        Integer failures = failedAttempts.getIfPresent(key);
        if (failures != null && failures >= maxFailedAttempts) {
            Duration lockAge = expiration.ageOf(key).orElse(Duration.ZERO);
            throw new TooManyRequestsException("Too many failed login attempts", lockoutDuration.minus(lockAge));
        }
    }

    public void recordFailure(String username) {
        failedAttempts.asMap().merge(normalize(username), 1, Integer::sum);
    }

    public void reset(String username) {
        failedAttempts.invalidate(normalize(username));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
