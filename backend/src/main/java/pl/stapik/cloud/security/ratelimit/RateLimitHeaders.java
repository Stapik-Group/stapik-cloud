package pl.stapik.cloud.security.ratelimit;

import jakarta.servlet.http.HttpServletResponse;

final class RateLimitHeaders {

    static final String LIMIT = "X-RateLimit-Limit";
    static final String REMAINING = "X-RateLimit-Remaining";
    static final String RESET = "X-RateLimit-Reset";

    private RateLimitHeaders() {
    }

    static void write(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(LIMIT, String.valueOf(decision.limit()));
        response.setHeader(REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(RESET, String.valueOf(decision.resetAfterSeconds()));
    }
}
