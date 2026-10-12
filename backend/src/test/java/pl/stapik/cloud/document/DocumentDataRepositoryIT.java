package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import org.springframework.dao.DataIntegrityViolationException;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentChangeSummary;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Optional<DocumentData> result = documentRepository.findByDocumentSlotIdAndPartitionKey(slot.getId(), DocumentData.DEFAULT_PARTITION_KEY);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getDocumentSlotId()).isEqualTo(slot.getId());
        assertThat(result.get().getContent()).isEqualTo("some content");
        assertThat(result.get().getContentHash()).isEqualTo("hash1");
    }

    @Test
    void shouldFindForUpdateByDocumentSlotIdAndPartitionKey() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId());

        // when
        Optional<DocumentData> result = documentRepository
                .findForUpdateByDocumentSlotIdAndPartitionKey(slot.getId(), DocumentData.DEFAULT_PARTITION_KEY);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("some content");
    }

    @Test
    void shouldReturnEmptyWhenDocumentSlotIdNotFound() {
        // when
        Optional<DocumentData> result = documentRepository.findByDocumentSlotIdAndPartitionKey(UUID.randomUUID(), DocumentData.DEFAULT_PARTITION_KEY);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldKeepSeparateDocumentsPerPartitionInOneSlot() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId(), DocumentData.DEFAULT_PARTITION_KEY, "main content", 12, null);
        createDocument(slot.getId(), "2023", "year 2023", 9, null);
        createDocument(slot.getId(), "2024", "year 2024", 9, null);

        // when
        Optional<DocumentData> mainDocument = documentRepository
                .findByDocumentSlotIdAndPartitionKey(slot.getId(), DocumentData.DEFAULT_PARTITION_KEY);
        Optional<DocumentData> partition2023 = documentRepository.findByDocumentSlotIdAndPartitionKey(slot.getId(), "2023");
        Optional<DocumentData> missingPartition = documentRepository.findByDocumentSlotIdAndPartitionKey(slot.getId(), "2022");

        // then
        assertThat(mainDocument).get().extracting(DocumentData::getContent).isEqualTo("main content");
        assertThat(partition2023).get().extracting(DocumentData::getContent).isEqualTo("year 2023");
        assertThat(missingPartition).isEmpty();
    }

    @Test
    void shouldRejectSecondDocumentWithTheSamePartitionInOneSlot() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId(), "2024", "first", 5, null);

        DocumentData duplicate = DocumentData.builder()
                .documentSlotId(slot.getId())
                .partitionKey("2024")
                .content("second")
                .contentHash("hash2")
                .updatedAt(Instant.now())
                .build();

        // when / then
        assertThatThrownBy(() -> documentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldListPartitionSummariesWithoutMainAndDeletedDocuments() {
        // given
        Instant updatedAt = Instant.parse("2026-07-18T10:00:00Z");
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId(), DocumentData.DEFAULT_PARTITION_KEY, "main content", 12, null);
        createDocument(slot.getId(), "2024", "year 2024", 9, null);
        createDocument(slot.getId(), "2023", "year 2023 longer", 16, null);
        createDocument(slot.getId(), "2022", "deleted year", 12, updatedAt);

        // when
        List<DocumentPartitionSummary> summaries = documentRepository.findPartitionSummaries(slot.getId());

        // then
        assertThat(summaries)
                .extracting(DocumentPartitionSummary::getPartitionKey)
                .containsExactly("2023", "2024");
        assertThat(summaries.getFirst().getSizeBytes()).isEqualTo(16);
        assertThat(summaries.getFirst().getContentHash()).isEqualTo("hash-2023");
        assertThat(summaries.getFirst().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldReturnNoPartitionSummariesForSlotWithOnlyMainDocument() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId());

        // when
        List<DocumentPartitionSummary> summaries = documentRepository.findPartitionSummaries(slot.getId());

        // then
        assertThat(summaries).isEmpty();
    }

    @Test
    void shouldFindOnlyDocumentsChangedAfterGivenMomentIncludingDeleted() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        Instant since = Instant.parse("2026-07-18T10:00:00Z");
        createDocument(slot.getId(), "2022", "old year", 8, null, since);
        createDocument(slot.getId(), "2023", "newer year", 10, null, since.plusSeconds(10));
        createDocument(slot.getId(), "2024", "deleted year", 12, since.plusSeconds(20), since.plusSeconds(20));

        // when
        List<DocumentChangeSummary> changes = documentRepository.findChanges(extension.getId(), since);

        // then
        assertThat(changes)
                .extracting(DocumentChangeSummary::getPartitionKey)
                .containsExactly("2023", "2024");
        assertThat(changes.getFirst().getSlotKey()).isEqualTo("slot-key");
        assertThat(changes.getFirst().getContentHash()).isEqualTo("hash-2023");
        assertThat(changes.getFirst().getDeletedAt()).isNull();
        assertThat(changes.get(1).getDeletedAt()).isEqualTo(since.plusSeconds(20));
    }

    @Test
    void shouldNotFindChangesOfOtherExtension() {
        // given
        ExtensionData extension = createExtension();
        DocumentSlotData slot = createDocumentSlot(extension.getId());
        createDocument(slot.getId());

        // when
        List<DocumentChangeSummary> changes = documentRepository
                .findChanges(UUID.randomUUID(), Instant.parse("2000-01-01T00:00:00Z"));

        // then
        assertThat(changes).isEmpty();
    }

    private void createDocument(UUID documentSlotId, String partitionKey, String content, long sizeBytes, Instant deletedAt) {
        createDocument(documentSlotId, partitionKey, content, sizeBytes, deletedAt, Instant.parse("2026-07-18T10:00:00Z"));
    }

    private void createDocument(UUID documentSlotId, String partitionKey, String content, long sizeBytes, Instant deletedAt, Instant updatedAt) {
        DocumentData documentData = DocumentData.builder()
                .documentSlotId(documentSlotId)
                .partitionKey(partitionKey)
                .content(content)
                .contentHash("hash-" + partitionKey)
                .sizeBytes(sizeBytes)
                .updatedAt(updatedAt)
                .deletedAt(deletedAt)
                .build();
        documentRepository.save(documentData);
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