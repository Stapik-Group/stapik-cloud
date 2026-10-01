package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DocumentVersionDataRepositoryIT extends AbstractIntegrationTest {

    private static final Instant FIRST_VERSION_SAVED_AT = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldDeleteAllVersionsExceptNewestOnes() {
        // given
        ExtensionData extension = createExtension();
        UUID documentId = createDocument(extension.getId(), "slot-key");
        saveVersions(documentId, 5);

        // when
        int deletedCount = documentVersionRepository.deleteAllExceptNewest(documentId, 3);

        // then
        assertThat(deletedCount).isEqualTo(2);
        List<DocumentVersionData> remainingVersions = documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentId);
        assertThat(remainingVersions)
                .extracting(DocumentVersionData::getContent)
                .containsExactly("version-5", "version-4", "version-3");
    }

    @Test
    void shouldKeepSingleNewestVersionWhenLimitIsOne() {
        // given
        ExtensionData extension = createExtension();
        UUID documentId = createDocument(extension.getId(), "slot-key");
        saveVersions(documentId, 4);

        // when
        documentVersionRepository.deleteAllExceptNewest(documentId, 1);

        // then
        List<DocumentVersionData> remainingVersions = documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentId);
        assertThat(remainingVersions)
                .extracting(DocumentVersionData::getContent)
                .containsExactly("version-4");
    }

    @Test
    void shouldNotDeleteAnythingWhenVersionCountDoesNotExceedLimit() {
        // given
        ExtensionData extension = createExtension();
        UUID documentId = createDocument(extension.getId(), "slot-key");
        saveVersions(documentId, 3);

        // when
        int deletedCount = documentVersionRepository.deleteAllExceptNewest(documentId, 3);

        // then
        assertThat(deletedCount).isZero();
        assertThat(documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentId)).hasSize(3);
    }

    @Test
    void shouldNotTouchVersionsOfOtherDocuments() {
        // given
        ExtensionData extension = createExtension();
        UUID prunedDocumentId = createDocument(extension.getId(), "pruned-slot");
        UUID untouchedDocumentId = createDocument(extension.getId(), "untouched-slot");
        saveVersions(prunedDocumentId, 4);
        saveVersions(untouchedDocumentId, 4);

        // when
        documentVersionRepository.deleteAllExceptNewest(prunedDocumentId, 2);

        // then
        assertThat(documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(prunedDocumentId)).hasSize(2);
        assertThat(documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(untouchedDocumentId)).hasSize(4);
    }

    private void saveVersions(UUID documentId, int versionCount) {
        for (int versionNumber = 1; versionNumber <= versionCount; versionNumber++) {
            DocumentVersionData version = DocumentVersionData.builder()
                    .documentId(documentId)
                    .content("version-" + versionNumber)
                    .savedAt(FIRST_VERSION_SAVED_AT.plus(versionNumber, ChronoUnit.MINUTES))
                    .reason(VersionReason.NORMAL_WRITE)
                    .build();
            documentVersionRepository.save(version);
        }
    }

    private UUID createDocument(UUID extensionId, String slotKey) {
        DocumentSlotData slot = DocumentSlotData.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .contentType(ContentType.TEXT)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .createdAt(Instant.now())
                .build();
        DocumentSlotData savedSlot = documentSlotRepository.save(slot);

        DocumentData documentData = DocumentData.builder()
                .documentSlotId(savedSlot.getId())
                .content("current content")
                .contentHash("hash")
                .updatedAt(Instant.now())
                .build();
        return documentRepository.save(documentData).getId();
    }

    private ExtensionData createExtension() {
        ExtensionData extensionData = ExtensionData.builder()
                .slug("version-test-slug")
                .displayName("Extension")
                .createdAt(Instant.now())
                .build();
        return extensionRepository.save(extensionData);
    }
}
