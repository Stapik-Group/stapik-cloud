package pl.stapik.cloud.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.admin.data.CreateExtensionRequest;
import pl.stapik.cloud.admin.data.ExtensionResponse;
import pl.stapik.cloud.common.mapper.DateTimeMapperImpl;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExtensionMapperTest {

    @Spy
    private DateTimeMapperImpl dateTimeMapper;

    @InjectMocks
    private ExtensionMapperImpl extensionMapper;

    @Test
    void shouldMapEntityToResponse() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-18T10:00:00Z");

        ExtensionData entity = new ExtensionData();
        entity.setId(id);
        entity.setSlug("test-slug");
        entity.setDisplayName("Test Display Name");
        entity.setIconGlyph("icon-test");
        entity.setColor("#FFFFFF");
        entity.setEnabled(true);
        entity.setCreatedAt(createdAt);

        // When
        ExtensionResponse response = extensionMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getSlug()).isEqualTo("test-slug");
        assertThat(response.getDisplayName()).isEqualTo("Test Display Name");
        assertThat(response.getIconGlyph()).isEqualTo("icon-test");
        assertThat(response.getColor()).isEqualTo("#FFFFFF");
        assertThat(response.getEnabled()).isTrue();
    }

    @Test
    void shouldMapCreateRequestToEntity() {
        // Given
        CreateExtensionRequest request = new CreateExtensionRequest();
        request.setSlug("new-slug");
        request.setDisplayName("New Extension");
        request.setIconGlyph("icon-new");
        request.setColor("#000000");

        // When
        ExtensionData entity = extensionMapper.toEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getSlug()).isEqualTo("new-slug");
        assertThat(entity.getDisplayName()).isEqualTo("New Extension");
        assertThat(entity.getIconGlyph()).isEqualTo("icon-new");
        assertThat(entity.getColor()).isEqualTo("#000000");
        assertThat(entity.getId()).isNull();
        assertThat(entity.isEnabled()).isFalse();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    void shouldReturnNullWhenMappingNullEntity() {
        // When
        ExtensionResponse response = extensionMapper.toResponse(null);

        // Then
        assertThat(response).isNull();
    }

    @Test
    void shouldReturnNullWhenMappingNullRequest() {
        // When
        ExtensionData entity = extensionMapper.toEntity(null);

        // Then
        assertThat(entity).isNull();
    }
}