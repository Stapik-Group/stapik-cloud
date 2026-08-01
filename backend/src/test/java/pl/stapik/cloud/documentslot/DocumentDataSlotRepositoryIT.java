package pl.stapik.cloud.documentslot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DocumentDataSlotRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldFindByExtensionIdAndSlotKey() {
        // given
        ExtensionData extension = createExtension("test-ext");
        createSlot(extension.getId(), "notes");
        createSlot(extension.getId(), "other-slot");

        // when
        Optional<DocumentSlotData> result = documentSlotRepository.findByExtensionIdAndSlotKey(extension.getId(), "notes");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getSlotKey()).isEqualTo("notes");
        assertThat(result.get().getExtensionId()).isEqualTo(extension.getId());
    }

    @Test
    void shouldReturnEmptyWhenSlotKeyNotFoundForExtension() {
        // given
        ExtensionData extension = createExtension("test-ext");
        createSlot(extension.getId(), "notes");

        // when
        Optional<DocumentSlotData> result = documentSlotRepository.findByExtensionIdAndSlotKey(extension.getId(), "missing-slot");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFindSlotBelongingToDifferentExtension() {
        // given
        ExtensionData extension = createExtension("test-ext");
        ExtensionData otherExtension = createExtension("other-ext");
        createSlot(otherExtension.getId(), "notes");

        // when
        Optional<DocumentSlotData> result = documentSlotRepository.findByExtensionIdAndSlotKey(extension.getId(), "notes");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindAllSlotsByExtensionId() {
        // given
        ExtensionData extension = createExtension("test-ext");
        ExtensionData otherExtension = createExtension("other-ext");

        createSlot(extension.getId(), "slot-1");
        createSlot(extension.getId(), "slot-2");
        createSlot(otherExtension.getId(), "slot-3");

        // when
        List<DocumentSlotData> result = documentSlotRepository.findByExtensionId(extension.getId());

        // then
        assertThat(result)
                .hasSize(2)
                .allMatch(slot -> slot.getExtensionId().equals(extension.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenExtensionHasNoSlots() {
        // given
        ExtensionData extension = createExtension("test-ext");

        // when
        List<DocumentSlotData> result = documentSlotRepository.findByExtensionId(extension.getId());

        // then
        assertThat(result).isEmpty();
    }

    private void createSlot(UUID extensionId, String slotKey) {
        DocumentSlotData slot = DocumentSlotData.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .contentType(ContentType.TEXT)
                .maxSizeBytes(1_048_576L)
                .versioningEnabled(true)
                .maxVersionsRetained(10)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .encryptionRequired(false)
                .createdAt(Instant.now())
                .build();
        documentSlotRepository.save(slot);
    }

    private ExtensionData createExtension(String slug) {
        ExtensionData ext = new ExtensionData();
        ext.setSlug(slug);
        ext.setDisplayName("Test Extension");
        ext.setEnabled(true);
        ext.setCreatedAt(Instant.now());
        return extensionRepository.save(ext);
    }
}