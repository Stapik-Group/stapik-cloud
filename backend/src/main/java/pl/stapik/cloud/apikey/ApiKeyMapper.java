package pl.stapik.cloud.apikey;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.openapitools.jackson.nullable.JsonNullable;
import pl.stapik.cloud.admin.data.ApiKeyCreatedResponse;
import pl.stapik.cloud.admin.data.ApiKeyResponse;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, JsonNullableMapper.class}
)
public interface ApiKeyMapper {

    @Mapping(target = "scope", expression = "java(pl.stapik.cloud.admin.data.ApiKeyScope.fromValue(apiKey.getScope().name()))")
    @Mapping(target = "ipAllowlist", expression = "java(wrapIpAllowlist(apiKey.getIpAllowlist()))")
    @Mapping(target = "lastUsedAt", expression = "java(wrapLastUsedAt(apiKey.getLastUsedAt()))")
    ApiKeyResponse toResponse(ApiKeyData apiKey);

    default JsonNullable<String> wrapIpAllowlist(String value) {
        return JsonNullable.of(value);
    }

    default JsonNullable<OffsetDateTime> wrapLastUsedAt(Instant value) {
        return JsonNullable.of(value == null ? null : value.atOffset(ZoneOffset.UTC));
    }

    default ApiKeyCreatedResponse toCreatedResponse(ApiKeyData apiKey, String rawKey) {
        return new ApiKeyCreatedResponse()
                .id(apiKey.getId())
                .label(apiKey.getLabel())
                .scope(pl.stapik.cloud.admin.data.ApiKeyScope.fromValue(apiKey.getScope().name()))
                .ipAllowlist(apiKey.getIpAllowlist())
                .revoked(apiKey.isRevoked())
                .lastUsedAt(apiKey.getLastUsedAt() == null ? null : apiKey.getLastUsedAt().atOffset(ZoneOffset.UTC))
                .createdAt(apiKey.getCreatedAt().atOffset(ZoneOffset.UTC))
                .rawKey(rawKey);
    }
}