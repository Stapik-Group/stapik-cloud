package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.common.mapper.DateTimeMapperImpl;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.internal.data.DocumentResponse;
import pl.stapik.cloud.internal.data.DocumentVersionResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.stapik.cloud.internal.data.VersionReason.MANUAL_RESTORE;

@ExtendWith(MockitoExtension.class)
class DocumentDataMapperTest {

    @Spy
    private DateTimeMapperImpl dateTimeMapper;

    @InjectMocks
    private DocumentMapperImpl documentMapper;

    @Test
    void shouldMapDocumentToResponse() {
        // Given
        Instant updatedAt = Instant.parse("2026-07-18T10:00:00Z");

        DocumentData documentData = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(UUID.randomUUID())
                .content("some content")
                .contentHash("hash-123")
                .updatedAt(updatedAt)
                .build();

        // When
        DocumentResponse response = documentMapper.toResponse(documentData, "slot-key-1");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSlotKey()).isEqualTo("slot-key-1");
        assertThat(response.getContent()).isEqualTo("some content");
        assertThat(response.getContentHash()).isEqualTo("hash-123");
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldMapVersionToVersionResponse() {
        // Given
        UUID versionId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-07-18T12:30:00Z");

        DocumentVersionData version = DocumentVersionData.builder()
                .id(versionId)
                .savedAt(savedAt)
                .reason(VersionReason.MANUAL_RESTORE)
                .build();

        // When
        DocumentVersionResponse response = documentMapper.toVersionResponse(version);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(versionId);
        assertThat(response.getSavedAt()).isEqualTo(savedAt.atOffset(ZoneOffset.UTC));
        assertThat(response.getReason()).isEqualTo(MANUAL_RESTORE);
    }

    @Test
    void shouldReturnNullWhenMappingNullDocumentAndSlotKey() {
        // When
        DocumentResponse response = documentMapper.toResponse(null, null);

        // Then
        assertThat(response).isNull();
    }

    @Test
    void shouldMapSlotKeyOnlyWhenDocumentIsNull() {
        // When
        DocumentResponse response = documentMapper.toResponse(null, "slot-key-only");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSlotKey()).isEqualTo("slot-key-only");
        assertThat(response.getContent()).isNull();
        assertThat(response.getContentHash()).isNull();
        assertThat(response.getUpdatedAt()).isNull();
    }

    @Test
    void shouldReturnNullWhenMappingNullVersion() {
        // When
        DocumentVersionResponse response = documentMapper.toVersionResponse(null);

        // Then
        assertThat(response).isNull();
    }
}