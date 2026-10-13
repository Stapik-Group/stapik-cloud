package pl.stapik.cloud.apikey.dto;

import pl.stapik.cloud.apikey.data.ApiKeyScope;

import java.time.Instant;
import java.util.UUID;

public interface ApiKeyActivity {
    UUID getId();
    String getLabel();
    String getKeyPrefix();
    ApiKeyScope getScope();
    UUID getExtensionId();
    String getExtensionName();
    Instant getLastUsedAt();
}
