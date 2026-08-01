package pl.stapik.cloud.apikey.dto;

import pl.stapik.cloud.apikey.data.ApiKeyData;

public record CreatedApiKey(ApiKeyData apiKey, String rawKey) { }
