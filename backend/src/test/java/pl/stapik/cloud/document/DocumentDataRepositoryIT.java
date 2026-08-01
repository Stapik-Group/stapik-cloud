package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DocumentDataRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldFindByDocumentSlotId() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId());

        // when
        Optional<DocumentData> result = documentRepository.findByDocumentSlotId(slot.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getDocumentSlotId()).isEqualTo(slot.getId());
        assertThat(result.get().getContent()).isEqualTo("some content");
        assertThat(result.get().getContentHash()).isEqualTo("hash1");
    }

    @Test
    void shouldReturnEmptyWhenDocumentSlotIdNotFound() {
        // when
        Optional<DocumentData> result = documentRepository.findByDocumentSlotId(UUID.randomUUID());

        // then
        assertThat(result).isEmpty();
    }

    private void createDocument(UUID documentSlotId) {
        DocumentData documentData = DocumentData.builder()
                .documentSlotId(documentSlotId)
                .content("some content")
                .contentHash("hash1")
                .updatedAt(Instant.now())
                .build();
        documentRepository.save(documentData);
    }

    private DocumentSlotData createDocumentSlot(UUID extensionUuid) {
        DocumentSlotData slot = DocumentSlotData.builder()
                .extensionId(extensionUuid)
                .slotKey("slot-key")
                .contentType(ContentType.JSON)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .createdAt(Instant.now())
                .build();
        return documentSlotRepository.save(slot);
    }

    private ExtensionData createExtension() {
        ExtensionData extensionData = ExtensionData.builder()
                .slug("some-slug")
                .displayName("Extension")
                .createdAt(Instant.now())
                .build();
        return extensionRepository.save(extensionData);
    }
}