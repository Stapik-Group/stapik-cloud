package pl.stapik.cloud.apikey.dto;

import lombok.Builder;
import lombok.Data;
import pl.stapik.cloud.apikey.data.ApiKeyScope;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CreateApiKeyInfo {
    private UUID extensionId;
    private String label;
    private ApiKeyScope scope;
    private String ipAllowlist;
    private Instant expiresAt;
}
