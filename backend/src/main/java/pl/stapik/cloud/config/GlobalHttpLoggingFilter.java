package pl.stapik.cloud.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import pl.stapik.cloud.config.logger.JsonLoggerBuilder;
import pl.stapik.cloud.config.logger.TruncatedLoggerBuilder;
import pl.stapik.cloud.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GlobalHttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger consoleLog = LoggerFactory.getLogger("HTTP_CONSOLE");
    private static final Logger fullLog = LoggerFactory.getLogger("HTTP_FULL");
    private static final String TRACE_ID_KEY = "traceId";

    private final TruncatedLoggerBuilder truncatedLogger;
    private final JsonLoggerBuilder jsonLogger;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = StringUtils.generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);

        CachedBodyHttpServletRequestWrapper cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        String requestBody = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);
        long startTime = System.nanoTime();

        try {
            logRequest(request, requestBody);
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            String responseBody = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            logResponse(request, cachedResponse, durationMs, responseBody);

            cachedResponse.copyBodyToResponse();
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private void logRequest(HttpServletRequest request, String requestBody) {
        String body = requestBody.isEmpty() ? "EMPTY_BODY" : requestBody;

        // stdout - truncated
        var truncatedRequest = truncatedLogger.createRequest(request.getMethod(), request.getRequestURI(), body);
        consoleLog.info(truncatedRequest);

        // file - full version
        var jsonRequest = jsonLogger.createRequest(request.getMethod(), request.getRequestURI(), body);
        fullLog.info("{}", jsonRequest);
    }

    private void logResponse(HttpServletRequest request, ContentCachingResponseWrapper response, long durationMs, String responseBody) {
        String body = responseBody.isEmpty() ? "EMPTY_BODY" : responseBody;

        // stdout - truncated
        var truncatedResponse = truncatedLogger.createResponse(request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, body);
        consoleLog.info(truncatedResponse);

        // file - full version
        var jsonResponse = jsonLogger.createResponse(request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, body);
        fullLog.info("{}", jsonResponse);
    }
}