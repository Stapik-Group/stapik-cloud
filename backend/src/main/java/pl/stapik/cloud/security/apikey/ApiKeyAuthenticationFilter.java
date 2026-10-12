package pl.stapik.cloud.security.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.common.crypto.ApiKeyHasher;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "x-api-key";
    private static final int PREFIX_LENGTH = 8;
    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeyRepository;
    private final ExtensionRepository extensionRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER_NAME);
        if (rawKey != null && rawKey.length() >= PREFIX_LENGTH) {
            String prefix = rawKey.substring(0, PREFIX_LENGTH);

            apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix)
                    .filter(apiKey -> apiKeyHasher.matches(rawKey, apiKey.getHashedKey()))
                    .filter(this::notExpired)
                    .filter(this::extensionEnabled)
                    .ifPresent(apiKey -> authenticate(apiKey, rawKey));
        }

        filterChain.doFilter(request, response);
    }

    private boolean notExpired(ApiKeyData apiKey) {
        return apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(Instant.now());
    }

    private boolean extensionEnabled(ApiKeyData apiKey) {
        return extensionRepository.existsByIdAndEnabledTrue(apiKey.getExtensionId());
    }

    private void authenticate(ApiKeyData apiKey, String rawKey) {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKey.getId(),
                apiKey.getExtensionId(),
                apiKey.getLabel(),
                mapKeyScope(apiKey.getScope())
        );
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(principal));

        recordUsage(apiKey, rawKey);
    }

    private void recordUsage(ApiKeyData apiKey, String rawKey) {
        Instant now = Instant.now();
        boolean upgradeLegacyHash = apiKeyHasher.isLegacy(apiKey.getHashedKey());
        boolean lastUsedStale = apiKey.getLastUsedAt() == null
                || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_UPDATE_INTERVAL));
        if (!upgradeLegacyHash && !lastUsedStale) {
            return;
        }

        if (upgradeLegacyHash) {
            apiKey.setHashedKey(apiKeyHasher.hash(rawKey));
        }
        apiKey.setLastUsedAt(now);
        apiKeyRepository.save(apiKey);
    }

    private pl.stapik.cloud.admin.data.ApiKeyScope mapKeyScope(ApiKeyScope scope) {
        if (ApiKeyScope.READ_ONLY.equals(scope)) {
            return pl.stapik.cloud.admin.data.ApiKeyScope.ONLY;
        } else if (ApiKeyScope.READ_WRITE.equals(scope)) {
            return pl.stapik.cloud.admin.data.ApiKeyScope.WRITE;
        }

        throw new IllegalArgumentException("Unknown api key scope");
    }
}
