package pl.stapik.cloud.documentslot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.impl.DocumentSlotServiceImpl;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentDataSlotServiceTest {

    @Mock
    private DocumentSlotRepository documentSlotRepository;

    @Mock
    private ExtensionRepository extensionRepository;

    @InjectMocks
    private DocumentSlotServiceImpl documentSlotServiceImpl;

    private static final UUID EXTENSION_ID = UUID.randomUUID();

    @Test
    void shouldListSlotsByExtension() {
        // given
        List<DocumentSlotData> slots = List.of(
                DocumentSlotData.builder().id(UUID.randomUUID()).extensionId(EXTENSION_ID).build()
        );
        when(documentSlotRepository.findByExtensionId(EXTENSION_ID)).thenReturn(slots);

        // when
        List<DocumentSlotData> result = documentSlotServiceImpl.listByExtension(EXTENSION_ID);

        // then
        assertThat(result).isEqualTo(slots);
    }

    @Test
    void shouldReturnEmptyListWhenExtensionHasNoSlots() {
        // given
        when(documentSlotRepository.findByExtensionId(EXTENSION_ID)).thenReturn(List.of());

        // when
        List<DocumentSlotData> result = documentSlotServiceImpl.listByExtension(EXTENSION_ID);

        // then
        assertThat(result).isEmpty();
    }

    // ---------- create ----------

    @Test
    void shouldCreateSlotWhenExtensionExists() {
        // given
        when(extensionRepository.existsById(EXTENSION_ID)).thenReturn(true);
        when(documentSlotRepository.save(any(DocumentSlotData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        DocumentSlotData result = documentSlotServiceImpl.create(
                DocumentIdentifier.of(EXTENSION_ID, "notes"),
                ContentType.TEXT,
                1_048_576L,
                true,
                10,
                ConflictStrategy.LAST_WRITE_WINS,
                false
        );

        // then
        assertThat(result.getExtensionId()).isEqualTo(EXTENSION_ID);
        assertThat(result.getSlotKey()).isEqualTo("notes");
        assertThat(result.getContentType()).isEqualTo(ContentType.TEXT);
        assertThat(result.getMaxSizeBytes()).isEqualTo(1_048_576L);
        assertThat(result.isVersioningEnabled()).isTrue();
        assertThat(result.getMaxVersionsRetained()).isEqualTo(10);
        assertThat(result.getConflictStrategy()).isEqualTo(ConflictStrategy.LAST_WRITE_WINS);
        assertThat(result.isEncryptionRequired()).isFalse();
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenExtensionDoesNotExistOnCreate() {
        // given
        when(extensionRepository.existsById(EXTENSION_ID)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> documentSlotServiceImpl.create(
                DocumentIdentifier.of(EXTENSION_ID, "notes"),
                ContentType.TEXT,
                1_048_576L,
                true,
                10,
                ConflictStrategy.LAST_WRITE_WINS,
                false
        ))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(EXTENSION_ID.toString());

        verify(documentSlotRepository, never()).save(any());
    }

    @Test
    void shouldDeleteSlotWhenBelongsToExtension() {
        // given
        UUID slotId = UUID.randomUUID();
        DocumentSlotData slot = DocumentSlotData.builder().id(slotId).extensionId(EXTENSION_ID).build();

        when(documentSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // when
        documentSlotServiceImpl.delete(EXTENSION_ID, slotId);

        // then
        verify(documentSlotRepository).delete(slot);
    }

    @Test
    void shouldThrowWhenSlotNotFoundOnDelete() {
        // given
        UUID slotId = UUID.randomUUID();
        when(documentSlotRepository.findById(slotId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> documentSlotServiceImpl.delete(EXTENSION_ID, slotId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(slotId.toString());

        verify(documentSlotRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenSlotBelongsToDifferentExtensionOnDelete() {
        // given
        UUID slotId = UUID.randomUUID();
        UUID otherExtensionId = UUID.randomUUID();
        DocumentSlotData slot = DocumentSlotData.builder().id(slotId).extensionId(otherExtensionId).build();

        when(documentSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // when / then
        assertThatThrownBy(() -> documentSlotServiceImpl.delete(EXTENSION_ID, slotId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(slotId.toString());

        verify(documentSlotRepository, never()).delete(any());
    }
}