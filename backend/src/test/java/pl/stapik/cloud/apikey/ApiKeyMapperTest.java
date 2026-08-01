package pl.stapik.cloud.apikey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import pl.stapik.cloud.admin.data.ApiKeyCreatedResponse;
import pl.stapik.cloud.admin.data.ApiKeyResponse;
import pl.stapik.cloud.admin.data.ApiKeyScope;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.common.mapper.DateTimeMapperImpl;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ApiKeyMapperTest {

    @Spy
    private DateTimeMapperImpl dateTimeMapper;

    @Spy
    private JsonNullableMapper jsonNullableMapper;

    @InjectMocks
    private ApiKeyMapperImpl apiKeyMapper;

    @Test
    void shouldMapEntityToResponse() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-18T10:00:00Z");

        ApiKeyData entity = new ApiKeyData();
        entity.setId(id);
        entity.setLabel("Test Label");
        entity.setScope(pl.stapik.cloud.apikey.data.ApiKeyScope.READ_ONLY);
        entity.setIpAllowlist("192.168.1.1");
        entity.setLastUsedAt(createdAt);
        entity.setCreatedAt(createdAt);
        entity.setRevoked(false);

        // When
        ApiKeyResponse response = apiKeyMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getLabel()).isEqualTo("Test Label");
        assertThat(response.getScope()).isEqualTo(ApiKeyScope.ONLY);
        assertThat(response.getIpAllowlist()).isEqualTo(JsonNullable.of("192.168.1.1"));
        assertThat(response.getLastUsedAt()).isEqualTo(JsonNullable.of(createdAt.atOffset(ZoneOffset.UTC)));
    }

    @Test
    void shouldMapToCreatedResponse() {
        // Given
        UUID id = UUID.randomUUID();
        String rawKey = "secret-raw-key";
        Instant createdAt = Instant.parse("2026-07-18T10:00:00Z");

        ApiKeyData entity = new ApiKeyData();
        entity.setId(id);
        entity.setLabel("New Key");
        entity.setScope(pl.stapik.cloud.apikey.data.ApiKeyScope.READ_WRITE);
        entity.setCreatedAt(createdAt);
        entity.setRevoked(false);

        // When
        ApiKeyCreatedResponse response = apiKeyMapper.toCreatedResponse(entity, rawKey);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getRawKey()).isEqualTo(rawKey);
        assertThat(response.getScope()).isEqualTo(ApiKeyScope.WRITE);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldReturnNullWhenMappingNullEntity() {
        // When
        ApiKeyResponse response = apiKeyMapper.toResponse(null);

        // Then
        assertThat(response).isNull();
    }
}