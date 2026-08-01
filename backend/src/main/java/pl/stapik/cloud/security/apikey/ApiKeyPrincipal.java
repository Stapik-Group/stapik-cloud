package pl.stapik.cloud.security.apikey;

import pl.stapik.cloud.admin.data.ApiKeyScope;

import java.util.UUID;

public record ApiKeyPrincipal(
        UUID apiKeyId,
        UUID extensionId,
        String keyLabel,
        ApiKeyScope scope) {}
