package pl.stapik.cloud.apikey;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.KeysApiDelegate;
import pl.stapik.cloud.admin.data.ApiKeyCreatedResponse;
import pl.stapik.cloud.admin.data.ApiKeyListResponse;
import pl.stapik.cloud.admin.data.ApiKeyResponse;
import pl.stapik.cloud.admin.data.CreateApiKeyRequest;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.apikey.dto.CreateApiKeyInfo;
import pl.stapik.cloud.apikey.dto.CreatedApiKey;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiKeyDelegate implements KeysApiDelegate {

    private final ApiKeyService apiKeyService;
    private final ApiKeyMapper apiKeyMapper;

    @Override
    public ResponseEntity<ApiKeyListResponse> listApiKeys(UUID extensionId) {
        List<ApiKeyResponse> keys = apiKeyService.listByExtension(extensionId).stream()
                .map(apiKeyMapper::toResponse)
                .toList();

        return ResponseEntity.ok(new ApiKeyListResponse().keys(keys));
    }

    @Override
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(UUID extensionId, CreateApiKeyRequest createApiKeyRequest) {
        ApiKeyScope scope = ApiKeyScope.fromValue(createApiKeyRequest.getScope().name());
        String ipAllowlist = createApiKeyRequest.getIpAllowlist().orElse(null);

        OffsetDateTime expiresAtValue = createApiKeyRequest.getExpiresAt().orElse(null);
        Instant expiresAt = expiresAtValue == null ? null : expiresAtValue.toInstant();

        CreateApiKeyInfo createApiKeyInfo = CreateApiKeyInfo.builder()
                .extensionId(extensionId)
                .label(createApiKeyRequest.getLabel())
                .scope(scope)
                .ipAllowlist(ipAllowlist)
                .expiresAt(expiresAt)
                .build();

        CreatedApiKey created = apiKeyService.create(createApiKeyInfo);
        ApiKeyCreatedResponse response = apiKeyMapper.toCreatedResponse(created.apiKey(), created.rawKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<Void> revokeApiKey(UUID extensionId, UUID keyId) {
        apiKeyService.revoke(extensionId, keyId);
        return ResponseEntity.noContent().build();
    }
}
