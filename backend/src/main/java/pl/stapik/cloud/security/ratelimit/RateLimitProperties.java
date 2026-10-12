package pl.stapik.cloud.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("stapik-cloud.rate-limit")
public record RateLimitProperties(Login login) {

    public record Login(int ipRequestsPerMinute, int maxFailedAttempts, Duration lockoutDuration) {
    }
}
