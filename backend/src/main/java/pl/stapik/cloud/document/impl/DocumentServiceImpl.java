package pl.stapik.cloud.document.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.document.DocumentRepository;
import pl.stapik.cloud.document.DocumentService;
import pl.stapik.cloud.document.DocumentVersionRepository;
import pl.stapik.cloud.document.conflict.ConflictDecision;
import pl.stapik.cloud.document.conflict.ConflictResolver;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
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
        return requireCurrentDocument(slot);
    }

    @Override
    public List<DocumentVersionData> listVersions(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocument(slot);
        return documentVersionRepository.findByDocumentIdOrderBySavedAtDesc(documentData.getId());
    }

    @Transactional
    @Override
    public WriteResult write(DocumentIdentifier identifier, String content, Instant clientLastKnownUpdate) {
        DocumentSlotData slot = requireSlot(identifier);
        Optional<DocumentData> rawExisting = documentRepository.findByDocumentSlotId(slot.getId());
        Optional<DocumentData> activeExisting = rawExisting.filter(doc -> doc.getDeletedAt() == null);

        if (activeExisting.isEmpty()) {
            DocumentData documentData = rawExisting
                    .map(deleted -> applyContent(deleted, content))
                    .orElseGet(() -> createDocument(slot.getId(), content));

            saveVersion(documentData.getId(), content, VersionReason.NORMAL_WRITE);
            return new WriteResult(documentData, false);
        }

        DocumentData existing = activeExisting.get();
        ConflictResolver resolver = resolverFor(slot.getConflictStrategy());
        ConflictDecision decision = resolver.resolve(existing.getUpdatedAt(), clientLastKnownUpdate);

        if (!decision.writeAccepted()) {
            if (decision.preserveDiscardedVersion()) {
                saveVersion(existing.getId(), content, VersionReason.CONFLICT_DISCARDED);
            }
            return new WriteResult(existing, true);
        }

        DocumentData saved = applyContent(existing, content);
        saveVersion(saved.getId(), content, VersionReason.NORMAL_WRITE);
        return new WriteResult(saved, false);
    }

    @Transactional
    @Override
    @Auditing(action = AuditAction.DOCUMENT_VERSION_RESTORED)
    public DocumentData restoreVersion(DocumentIdentifier identifier, UUID versionId) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocument(slot);

        DocumentVersionData version = documentVersionRepository.findById(versionId)
                .filter(v -> v.getDocumentId().equals(documentData.getId()))
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));

        DocumentData saved = applyContent(documentData, version.getContent());
        saveVersion(saved.getId(), version.getContent(), VersionReason.MANUAL_RESTORE);
        return saved;
    }

    @Transactional
    @Override
    public void delete(DocumentIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier);
        DocumentData documentData = requireCurrentDocument(slot);
        documentData.setDeletedAt(Instant.now());
        documentRepository.save(documentData);
    }

    private DocumentData createDocument(UUID slotId, String content) {
        DocumentData documentData = DocumentData.builder()
                .documentSlotId(slotId)
                .content(content)
                .contentHash(hash(content))
                .updatedAt(Instant.now())
                .build();

        return documentRepository.save(documentData);
    }

    private DocumentData applyContent(DocumentData documentData, String content) {
        documentData.setContent(content);
        documentData.setContentHash(hash(content));
        documentData.setUpdatedAt(Instant.now());
        documentData.setDeletedAt(null);
        return documentRepository.save(documentData);
    }

    private void saveVersion(UUID documentId, String content, VersionReason reason) {
        DocumentVersionData version = DocumentVersionData.builder()
                .documentId(documentId)
                .content(content)
                .savedAt(Instant.now())
                .reason(reason)
                .build();

        documentVersionRepository.save(version);
    }

    private DocumentData requireCurrentDocument(DocumentSlotData slot) {
        return documentRepository.findByDocumentSlotId(slot.getId())
                .filter(doc -> doc.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Document not found for slot: " + slot.getSlotKey()));
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