package pl.stapik.cloud.document.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.document.DocumentModifiedException;
import pl.stapik.cloud.document.DocumentRepository;
import pl.stapik.cloud.document.DocumentService;
import pl.stapik.cloud.document.DocumentVersionRepository;
import pl.stapik.cloud.document.conflict.ConflictDecision;
import pl.stapik.cloud.document.conflict.ConflictResolver;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.diff.JsonDiffer;
import pl.stapik.cloud.document.dto.DocumentChangeSummary;
import pl.stapik.cloud.document.dto.DocumentComparison;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;
import pl.stapik.cloud.document.dto.DocumentSnapshot;
import pl.stapik.cloud.document.dto.DocumentWriter;
import pl.stapik.cloud.document.dto.WriteResult;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentSlotRepository documentSlotRepository;
    private final List<ConflictResolver> conflictResolvers;

    @Override
    public DocumentData getCurrent(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        return requireCurrentDocument(slot, identifier);
    }

    @Override
    public List<DocumentVersionData> listVersions(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocument(slot, identifier);
        return documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentData.getId());
    }

    @Override
    public DocumentComparison compareVersions(DocumentIdentifier identifier, UUID fromVersionId, UUID toVersionId) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocument(slot, identifier);

        DocumentSnapshot from = DocumentSnapshot.ofVersion(requireVersion(documentData, fromVersionId));
        DocumentSnapshot to = toVersionId == null
                ? DocumentSnapshot.ofCurrent(documentData)
                : DocumentSnapshot.ofVersion(requireVersion(documentData, toVersionId));

        return new DocumentComparison(from, to, JsonDiffer.diff(from.content(), to.content()));
    }

    @Transactional
    @Override
    public WriteResult write(DocumentIdentifier identifier, String content, String clientContentHash, DocumentWriter writer) {
        DocumentSlotData slot = requireSlot(identifier);
        Optional<DocumentData> rawExisting = documentRepository
                .findForUpdateByDocumentSlotIdAndPartitionKey(slot.getId(), identifier.getPartitionKey());
        Optional<DocumentData> activeExisting = rawExisting.filter(doc -> doc.getDeletedAt() == null);

        if (activeExisting.isEmpty()) {
            DocumentData documentData = rawExisting
                    .map(deleted -> applyContent(deleted, content))
                    .orElseGet(() -> createDocument(slot.getId(), identifier.getPartitionKey(), content));

            saveVersion(slot, documentData.getId(), content, VersionReason.NORMAL_WRITE, writer);
            return new WriteResult(documentData, false);
        }

        DocumentData existing = activeExisting.get();
        if (isUnchanged(existing, content)) {
            return new WriteResult(existing, false);
        }

        ConflictResolver resolver = resolverFor(slot.getConflictStrategy());
        ConflictDecision decision = resolver.resolve(existing.getContentHash(), clientContentHash);

        if (!decision.writeAccepted()) {
            if (decision.preserveDiscardedVersion()) {
                saveVersion(slot, existing.getId(), content, VersionReason.CONFLICT_DISCARDED, writer);
            }
            return new WriteResult(existing, true);
        }

        DocumentData saved = applyContent(existing, content);
        saveVersion(slot, saved.getId(), content, VersionReason.NORMAL_WRITE, writer);
        return new WriteResult(saved, false);
    }

    @Transactional
    @Override
    @Auditing(action = AuditAction.DOCUMENT_VERSION_RESTORED)
    public DocumentData restoreVersion(DocumentIdentifier identifier, UUID versionId, DocumentWriter writer) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocumentForUpdate(slot, identifier);

        DocumentVersionData version = requireVersion(documentData, versionId);

        DocumentData saved = applyContent(documentData, version.getContent());
        saveVersion(slot, saved.getId(), version.getContent(), VersionReason.MANUAL_RESTORE, writer);
        return saved;
    }

    @Transactional
    @Override
    @Auditing(action = AuditAction.DOCUMENT_CONTENT_EDITED)
    public DocumentData updateContent(DocumentIdentifier identifier, String content, String clientContentHash) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData existing = requireCurrentDocumentForUpdate(slot, identifier);

        if (isUnchanged(existing, content)) {
            return existing;
        }

        if (!existing.getContentHash().equals(clientContentHash)) {
            throw new DocumentModifiedException(slot.getSlotKey());
        }

        DocumentData saved = applyContent(existing, content);
        saveVersion(slot, saved.getId(), content, VersionReason.NORMAL_WRITE, DocumentWriter.NONE);
        return saved;
    }

    @Override
    public List<DocumentPartitionSummary> listPartitions(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        return documentRepository.findPartitionSummaries(slot.getId());
    }

    @Override
    public List<DocumentChangeSummary> listChanges(UUID extensionId, Instant since) {
        return documentRepository.findChanges(extensionId, since);
    }

    @Transactional
    @Override
    public void delete(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocumentForUpdate(slot, identifier);
        Instant deletedAt = Instant.now();
        documentData.setDeletedAt(deletedAt);
        documentData.setUpdatedAt(deletedAt);
        documentRepository.save(documentData);
    }

    private boolean isUnchanged(DocumentData existing, String content) {
        return hash(content).equals(existing.getContentHash());
    }

    private DocumentData createDocument(UUID slotId, String partitionKey, String content) {
        DocumentData documentData = DocumentData.builder()
                .documentSlotId(slotId)
                .partitionKey(partitionKey)
                .content(content)
                .contentHash(hash(content))
                .sizeBytes(sizeInBytes(content))
                .updatedAt(Instant.now())
                .build();

        return documentRepository.save(documentData);
    }

    private DocumentData applyContent(DocumentData documentData, String content) {
        documentData.setContent(content);
        documentData.setContentHash(hash(content));
        documentData.setSizeBytes(sizeInBytes(content));
        documentData.setUpdatedAt(Instant.now());
        documentData.setDeletedAt(null);
        return documentRepository.save(documentData);
    }

    private void saveVersion(DocumentSlotData slot, UUID documentId, String content, VersionReason reason, DocumentWriter writer) {
        DocumentVersionData version = DocumentVersionData.builder()
                .documentId(documentId)
                .content(content)
                .savedAt(Instant.now())
                .reason(reason)
                .deviceId(writer.deviceId())
                .deviceLabel(writer.deviceLabel())
                .build();

        documentVersionRepository.save(version);
        documentVersionRepository.deleteAllExceptNewest(documentId, slot.getMaxVersionsRetained());
    }

    private DocumentVersionData requireVersion(DocumentData documentData, UUID versionId) {
        return documentVersionRepository.findById(versionId)
                .filter(version -> version.getDocumentId().equals(documentData.getId()))
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));
    }

    private DocumentData requireCurrentDocument(DocumentSlotData slot, DocumentIdentifier identifier) {
        return requireActive(documentRepository
                .findByDocumentSlotIdAndPartitionKey(slot.getId(), identifier.getPartitionKey()), slot, identifier);
    }

    private DocumentData requireCurrentDocumentForUpdate(DocumentSlotData slot, DocumentIdentifier identifier) {
        return requireActive(documentRepository
                .findForUpdateByDocumentSlotIdAndPartitionKey(slot.getId(), identifier.getPartitionKey()), slot, identifier);
    }

    private DocumentData requireActive(Optional<DocumentData> document, DocumentSlotData slot, DocumentIdentifier identifier) {
        return document
                .filter(doc -> doc.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException(documentNotFoundMessage(slot, identifier)));
    }

    private String documentNotFoundMessage(DocumentSlotData slot, DocumentIdentifier identifier) {
        String message = "Document not found for slot: " + slot.getSlotKey();
        boolean isDefaultPartition = DocumentData.DEFAULT_PARTITION_KEY.equals(identifier.getPartitionKey());
        return isDefaultPartition ? message : message + ", partition: " + identifier.getPartitionKey();
    }

    private DocumentSlotData requireSlot(DocumentIdentifier identifier) {
        return documentSlotRepository.findByExtensionIdAndSlotKey(identifier.getExtensionId(), identifier.getSlotKey())
                .orElseThrow(() -> new NoSuchElementException("Document slot not found: " + identifier.getSlotKey()));
    }

    private ConflictResolver resolverFor(ConflictStrategy strategy) {
        return conflictResolvers.stream()
                .filter(resolver -> resolver.supports() == strategy)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConflictResolver for strategy: " + strategy));
    }

    private long sizeInBytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}