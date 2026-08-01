package pl.stapik.cloud.apikey.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.apikey.*;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.dto.CreateApiKeyInfo;
import pl.stapik.cloud.apikey.dto.CreatedApiKey;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.common.crypto.HashingService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final HashingService hashingService;
    private final Clock clock;

    @Override
    public List<ApiKeyData> listByExtension(UUID extensionId) {
        return apiKeyRepository.findByExtensionId(extensionId);
    }

    @Override
    @Auditing(action = AuditAction.API_KEY_CREATED)
    public CreatedApiKey create(CreateApiKeyInfo createApiKeyInfo) {
        ApiKeyGenerator.GeneratedKey generated = apiKeyGenerator.generate();
        ApiKeyData apiKey = createApiKey(createApiKeyInfo, generated);
        return new CreatedApiKey(apiKeyRepository.save(apiKey), generated.rawKey());
    }

    @Override
    @Auditing(action = AuditAction.API_KEY_REVOKED)
    public void revoke(UUID extensionId, UUID keyId) {
        ApiKeyData apiKey = getApiKey(extensionId, keyId);
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
    }

    private ApiKeyData createApiKey(CreateApiKeyInfo createApiKeyInfo, ApiKeyGenerator.GeneratedKey generated) {
        return ApiKeyData.builder()
                .extensionId(createApiKeyInfo.getExtensionId())
                .label(createApiKeyInfo.getLabel())
                .keyPrefix(generated.prefix())
                .hashedKey(hashingService.hash(generated.rawKey()))
                .scope(createApiKeyInfo.getScope())
                .ipAllowlist(createApiKeyInfo.getIpAllowlist())
                .expiresAt(createApiKeyInfo.getExpiresAt())
                .revoked(false)
                .createdAt(Instant.now(clock))
                .build();
    }

    private @NonNull ApiKeyData getApiKey(UUID extensionId, UUID keyId) {
        return apiKeyRepository.findById(keyId)
                .filter(key -> key.getExtensionId().equals(extensionId))
                .orElseThrow(() -> new NoSuchElementException("Api key not found: " + keyId));
    }
}
