package pl.stapik.cloud.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_URIS = Set.of("/api/admin/auth/login", "/api/admin/auth/login/two-factor");

    private final KeyedRateLimiter ipRateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !RATE_LIMITED_URIS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        RateLimitDecision decision = ipRateLimiter.consume(request.getRemoteAddr());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        exceptionResolver.resolveException(request, response, null,
                new TooManyRequestsException("Too many login requests", decision.retryAfter()));
    }
}
