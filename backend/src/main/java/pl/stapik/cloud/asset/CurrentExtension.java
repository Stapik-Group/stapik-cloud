package pl.stapik.cloud.asset;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.util.Optional;
import java.util.UUID;

final class CurrentExtension {

    private CurrentExtension() {
    }

    static UUID id() {
        return Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(ApiKeyPrincipal.class::cast)
                .map(ApiKeyPrincipal::extensionId)
                .orElseThrow();
    }
}
