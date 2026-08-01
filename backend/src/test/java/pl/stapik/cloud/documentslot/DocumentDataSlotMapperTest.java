package pl.stapik.cloud.documentslot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.stapik.cloud.admin.data.DocumentSlotResponse;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDataSlotMapperTest {

    private final DocumentSlotMapper documentSlotMapper = new DocumentSlotMapperImpl();

    @Test
    void shouldMapSlotToResponse() {
        // given
        UUID id = UUID.randomUUID();
        DocumentSlotData slot = DocumentSlotData.builder()
                .id(id)
                .extensionId(UUID.randomUUID())
                .slotKey("notes")
                .contentType(ContentType.TEXT)
                .filenamePattern("*.txt")
                .maxSizeBytes(1_048_576L)
                .versioningEnabled(true)
                .maxVersionsRetained(10)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .encryptionRequired(false)
                .createdAt(Instant.now())
                .build();

        // when
        DocumentSlotResponse response = documentSlotMapper.toResponse(slot);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getSlotKey()).isEqualTo("notes");
        assertThat(response.getContentType()).isEqualTo(pl.stapik.cloud.admin.data.ContentType.TEXT);
        assertThat(response.getMaxSizeBytes()).isEqualTo(1_048_576L);
        assertThat(response.getVersioningEnabled()).isTrue();
        assertThat(response.getMaxVersionsRetained()).isEqualTo(10);
        assertThat(response.getConflictStrategy()).isEqualTo(pl.stapik.cloud.admin.data.ConflictStrategy.WINS);
        assertThat(response.getEncryptionRequired()).isFalse();
    }

    @Test
    void shouldMapShadowCopyConflictStrategy() {
        // given
        DocumentSlotData slot = DocumentSlotData.builder()
                .id(UUID.randomUUID())
                .slotKey("shadow-slot")
                .contentType(ContentType.JSON)
                .maxSizeBytes(2048L)
                .versioningEnabled(false)
                .maxVersionsRetained(5)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY)
                .encryptionRequired(true)
                .createdAt(Instant.now())
                .build();

        // when
        DocumentSlotResponse response = documentSlotMapper.toResponse(slot);

        // then
        assertThat(response.getConflictStrategy()).isEqualTo(pl.stapik.cloud.admin.data.ConflictStrategy.WINS_WITH_SHADOW_COPY);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void shouldMapAllContentTypes(ContentType contentType) {
        // given
        DocumentSlotData slot = DocumentSlotData.builder()
                .id(UUID.randomUUID())
                .slotKey("slot")
                .contentType(contentType)
                .maxSizeBytes(1024L)
                .versioningEnabled(true)
                .maxVersionsRetained(1)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .encryptionRequired(false)
                .createdAt(Instant.now())
                .build();

        // when
        DocumentSlotResponse response = documentSlotMapper.toResponse(slot);

        // then
        assertThat(response.getContentType().name()).isEqualTo(contentType.name());
    }

    @Test
    void shouldReturnNullWhenMappingNullSlot() {
        // when
        DocumentSlotResponse response = documentSlotMapper.toResponse(null);

        // then
        assertThat(response).isNull();
    }
}