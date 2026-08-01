package pl.stapik.cloud.apikey;

import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.dto.CreateApiKeyInfo;
import pl.stapik.cloud.apikey.dto.CreatedApiKey;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {
    List<ApiKeyData> listByExtension(UUID extensionId);
    CreatedApiKey create(CreateApiKeyInfo request);
    void revoke(UUID extensionId, UUID keyId);
}
