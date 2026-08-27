package pl.stapik.cloud.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class GlobalHttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GlobalHttpLoggingFilter.class);
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);

        CachedBodyHttpServletRequestWrapper cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        String requestBody = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);

        log.info("[REQUEST] {} {} | Request Payload: {}",
                request.getMethod(),
                request.getRequestURI(),
                requestBody.isEmpty() ? "EMPTY_BODY" : requestBody);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String responseBody = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);

            log.info("[RESPONSE] {} {} | Status: {} | Duration: {}ms | Response Payload: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    cachedResponse.getStatus(),
                    duration,
                    responseBody.isEmpty() ? "EMPTY_BODY" : responseBody);

            cachedResponse.copyBodyToResponse();
            MDC.remove(TRACE_ID_KEY);
        }
    }
}