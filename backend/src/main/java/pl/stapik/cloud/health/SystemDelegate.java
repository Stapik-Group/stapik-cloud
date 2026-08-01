package pl.stapik.cloud.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;
import pl.stapik.cloud.internal.api.SystemApiDelegate;
import pl.stapik.cloud.internal.data.HealthResponse;
import pl.stapik.cloud.internal.data.MeResponse;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SystemDelegate implements SystemApiDelegate {

    private final ExtensionRepository extensionRepository;

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = new HealthResponse()
                .status("UP")
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MeResponse> getMe() {
        ApiKeyPrincipal principal = Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(p -> (ApiKeyPrincipal) p)
                .orElseThrow();

        ExtensionData extension = extensionRepository.findById(principal.extensionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Extension not found for authenticated key: " + principal.extensionId()));

        MeResponse response = new MeResponse()
                .extensionId(principal.extensionId())
                .extensionSlug(extension.getSlug())
                .keyLabel(principal.keyLabel())
                .scope(MeResponse.ScopeEnum.valueOf(principal.scope().name()));

        return ResponseEntity.ok(response);
    }
}
