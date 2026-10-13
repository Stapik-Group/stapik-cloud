package pl.stapik.cloud.asset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AssetChunkSizeLimitFilter extends OncePerRequestFilter {

    private final String uploadsPathPrefix;
    private final long maxChunkBytes;

    public AssetChunkSizeLimitFilter(AssetUploadProperties properties) {
        this.uploadsPathPrefix = properties.basePath() + "/";
        this.maxChunkBytes = properties.maxChunkSize().toBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.PATCH.matches(request.getMethod()) || !request.getRequestURI().startsWith(uploadsPathPrefix);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();

        if (contentLength < 0) {
            response.sendError(HttpStatus.LENGTH_REQUIRED.value());
        } else if (contentLength > maxChunkBytes) {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
