package pl.stapik.cloud.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.io.IOException;

@RequiredArgsConstructor
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {

    private final KeyedRateLimiter apiKeyRateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = apiKeyRateLimiter.consume(principal.apiKeyId().toString());
        RateLimitHeaders.write(response, decision);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        exceptionResolver.resolveException(request, response, null,
                new TooManyRequestsException(
                        "API key rate limit of %d requests per minute exceeded".formatted(decision.limit()), decision.retryAfter()));
    }
}
