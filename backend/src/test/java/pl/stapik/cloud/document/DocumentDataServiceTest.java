package pl.stapik.cloud.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.document.conflict.ConflictDecision;
import pl.stapik.cloud.document.conflict.ConflictResolver;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.document.dto.WriteResult;
import pl.stapik.cloud.document.impl.DocumentServiceImpl;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentDataServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentSlotRepository documentSlotRepository;

    @Mock
    private ConflictResolver conflictResolver;

    private DocumentServiceImpl documentServiceImpl;

    private static final UUID EXTENSION_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final String SLOT_KEY = "slot-key";

    @BeforeEach
    void setUp() {
        documentServiceImpl = new DocumentServiceImpl(
                documentRepository,
                documentVersionRepository,
                documentSlotRepository,
                List.of(conflictResolver)
        );
    }

    private DocumentSlotData slot(ConflictStrategy strategy) {
        return DocumentSlotData.builder()
                .id(SLOT_ID)
                .extensionId(EXTENSION_ID)
                .slotKey(SLOT_KEY)
                .conflictStrategy(strategy)
                .build();
    }

    @Test
    void shouldGetCurrentDocument() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        DocumentData documentData = DocumentData.builder().id(UUID.randomUUID()).documentSlotId(SLOT_ID).build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY)).thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));

        // when
        DocumentData result = documentServiceImpl.getCurrent(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY));

        // then
        assertThat(result).isEqualTo(documentData);
    }

    @Test
    void shouldThrowWhenSlotNotFoundOnGetCurrent() {
        // given
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.empty());

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.getCurrent(documentIdentifier))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(SLOT_KEY);

        verifyNoInteractions(documentRepository);
    }

    @Test
    void shouldThrowWhenDocumentNotFoundOnGetCurrent() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.empty());

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.getCurrent(documentIdentifier))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldThrowWhenDocumentIsDeletedOnGetCurrent() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        DocumentData deleted = DocumentData.builder().id(UUID.randomUUID()).documentSlotId(SLOT_ID)
                .deletedAt(Instant.now()).build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(deleted));

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.getCurrent(documentIdentifier))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldListVersionsOrderedBySavedAtDesc() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        UUID documentId = UUID.randomUUID();
        DocumentData documentData = DocumentData.builder().id(documentId).documentSlotId(SLOT_ID).build();
        List<DocumentVersionData> versions = List.of(
                DocumentVersionData.builder().id(UUID.randomUUID()).documentId(documentId).build()
        );

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));
        when(documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentId)).thenReturn(versions);

        // when
        List<DocumentVersionData> result = documentServiceImpl.listVersions(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY));

        // then
        assertThat(result).isEqualTo(versions);
    }

    @Test
    void shouldCreateNewDocumentWhenNoneExists() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.empty());
        when(documentRepository.save(any(DocumentData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        WriteResult result = documentServiceImpl.write(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), "content", null);

        // then
        assertThat(result.conflict()).isFalse();
        assertThat(result.documentData().getDocumentSlotId()).isEqualTo(SLOT_ID);
        assertThat(result.documentData().getContent()).isEqualTo("content");
        assertThat(result.documentData().getDeletedAt()).isNull();

        ArgumentCaptor<DocumentVersionData> versionCaptor = ArgumentCaptor.forClass(DocumentVersionData.class);
        verify(documentVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getReason()).isEqualTo(VersionReason.NORMAL_WRITE);
        assertThat(versionCaptor.getValue().getContent()).isEqualTo("content");

        verifyNoInteractions(conflictResolver);
    }

    @Test
    void shouldReactivateSoftDeletedDocumentOnWrite() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        DocumentData deleted = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .content("old")
                .deletedAt(Instant.now())
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(deleted));
        when(documentRepository.save(any(DocumentData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        WriteResult result = documentServiceImpl.write(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), "new content", null);

        // then
        assertThat(result.conflict()).isFalse();
        assertThat(result.documentData().getId()).isEqualTo(deleted.getId());
        assertThat(result.documentData().getContent()).isEqualTo("new content");
        assertThat(result.documentData().getDeletedAt()).isNull();

        verify(documentVersionRepository).save(argThat(v -> v.getReason() == VersionReason.NORMAL_WRITE));
        verifyNoInteractions(conflictResolver);
    }

    @Test
    void shouldOverwriteExistingDocumentWhenWriteAccepted() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnown = Instant.parse("2026-07-18T10:00:00Z");

        DocumentData existing = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .content("old")
                .updatedAt(existingUpdatedAt)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(existing));
        when(conflictResolver.supports()).thenReturn(ConflictStrategy.LAST_WRITE_WINS);
        when(conflictResolver.resolve(existingUpdatedAt, clientLastKnown)).thenReturn(ConflictDecision.accept());
        when(documentRepository.save(any(DocumentData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        WriteResult result = documentServiceImpl.write(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), "new content", clientLastKnown);

        // then
        assertThat(result.conflict()).isFalse();
        assertThat(result.documentData().getContent()).isEqualTo("new content");

        verify(documentVersionRepository).save(argThat(v -> v.getReason() == VersionReason.NORMAL_WRITE));
    }

    @Test
    void shouldPreserveDiscardedVersionOnConflict() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY);
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnown = Instant.parse("2026-07-18T09:00:00Z");

        DocumentData existing = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .content("current content")
                .updatedAt(existingUpdatedAt)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(existing));
        when(conflictResolver.supports()).thenReturn(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY);
        when(conflictResolver.resolve(existingUpdatedAt, clientLastKnown))
                .thenReturn(new ConflictDecision(false, true));

        // when
        WriteResult result = documentServiceImpl.write(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), "conflicting content", clientLastKnown);

        // then
        assertThat(result.conflict()).isTrue();
        assertThat(result.documentData()).isEqualTo(existing);

        verify(documentVersionRepository).save(argThat(v ->
                v.getReason() == VersionReason.CONFLICT_DISCARDED
                        && v.getContent().equals("conflicting content")
                        && v.getDocumentId().equals(existing.getId())
        ));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldNotPreserveDiscardedVersionOnConflictWhenNotRequired() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        Instant existingUpdatedAt = Instant.parse("2026-07-18T10:00:00Z");
        Instant clientLastKnown = Instant.parse("2026-07-18T09:00:00Z");

        DocumentData existing = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .content("current content")
                .updatedAt(existingUpdatedAt)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(existing));
        when(conflictResolver.supports()).thenReturn(ConflictStrategy.LAST_WRITE_WINS);
        when(conflictResolver.resolve(existingUpdatedAt, clientLastKnown))
                .thenReturn(new ConflictDecision(false, false));

        // when
        WriteResult result = documentServiceImpl.write(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), "conflicting content", clientLastKnown);

        // then
        assertThat(result.conflict()).isTrue();
        assertThat(result.documentData()).isEqualTo(existing);

        verifyNoInteractions(documentVersionRepository);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoResolverSupportsStrategy() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY);
        DocumentData existing = DocumentData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .updatedAt(Instant.now())
                .build();
        Instant clientLastKnown = Instant.now();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(existing));
        when(conflictResolver.supports()).thenReturn(ConflictStrategy.LAST_WRITE_WINS);

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.write(documentIdentifier, "content", clientLastKnown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAST_WRITE_WINS_WITH_SHADOW_COPY");
    }

    @Test
    void shouldRestoreVersion() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        UUID documentId = UUID.randomUUID();
        DocumentData documentData = DocumentData.builder().id(documentId).documentSlotId(SLOT_ID).content("current").build();
        UUID versionId = UUID.randomUUID();
        DocumentVersionData version = DocumentVersionData.builder()
                .id(versionId)
                .documentId(documentId)
                .content("restored content")
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));
        when(documentVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(documentRepository.save(any(DocumentData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        DocumentData result = documentServiceImpl.restoreVersion(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY), versionId);

        // then
        assertThat(result.getContent()).isEqualTo("restored content");

        verify(documentVersionRepository).save(argThat(v ->
                v.getReason() == VersionReason.MANUAL_RESTORE
                        && v.getContent().equals("restored content")
        ));
    }

    @Test
    void shouldThrowWhenVersionNotFoundOnRestore() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        UUID documentId = UUID.randomUUID();
        DocumentData documentData = DocumentData.builder().id(documentId).documentSlotId(SLOT_ID).build();
        UUID versionId = UUID.randomUUID();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));
        when(documentVersionRepository.findById(versionId)).thenReturn(Optional.empty());

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.restoreVersion(documentIdentifier, versionId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(versionId.toString());

        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenVersionBelongsToDifferentDocumentOnRestore() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        UUID documentId = UUID.randomUUID();
        DocumentData documentData = DocumentData.builder().id(documentId).documentSlotId(SLOT_ID).build();
        UUID versionId = UUID.randomUUID();
        DocumentVersionData versionFromOtherDocument = DocumentVersionData.builder()
                .id(versionId)
                .documentId(UUID.randomUUID())
                .content("other doc content")
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));
        when(documentVersionRepository.findById(versionId)).thenReturn(Optional.of(versionFromOtherDocument));

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.restoreVersion(documentIdentifier, versionId))
                .isInstanceOf(NoSuchElementException.class);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldSoftDeleteDocument() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        DocumentData documentData = DocumentData.builder().id(UUID.randomUUID()).documentSlotId(SLOT_ID).build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.of(documentData));
        when(documentRepository.save(any(DocumentData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        documentServiceImpl.delete(DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY));

        // then
        verify(documentRepository).save(argThat(d -> d.getDeletedAt() != null));
    }

    @Test
    void shouldThrowWhenDocumentNotFoundOnDelete() {
        // given
        DocumentSlotData slot = slot(ConflictStrategy.LAST_WRITE_WINS);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(documentRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(Optional.empty());

        // when / then
        DocumentIdentifier documentIdentifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> documentServiceImpl.delete(documentIdentifier))
                .isInstanceOf(NoSuchElementException.class);

        verify(documentRepository, never()).save(any());
    }
}