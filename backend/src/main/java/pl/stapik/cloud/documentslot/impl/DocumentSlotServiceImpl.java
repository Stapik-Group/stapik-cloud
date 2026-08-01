package pl.stapik.cloud.documentslot.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.DocumentSlotService;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentSlotServiceImpl implements DocumentSlotService {

    private final DocumentSlotRepository documentSlotRepository;
    private final ExtensionRepository extensionRepository;

    @Override
    public List<DocumentSlotData> listByExtension(UUID extensionId) {
        return documentSlotRepository.findByExtensionId(extensionId);
    }

    @Override
    @Auditing(action = AuditAction.DOCUMENT_SLOT_CREATED)
    public DocumentSlotData create(
            DocumentIdentifier documentIdentifier,
            ContentType contentType,
            long maxSizeBytes,
            boolean versioningEnabled,
            int maxVersionsRetained,
            ConflictStrategy conflictStrategy,
            boolean encryptionRequired
    ) {
        if (!extensionRepository.existsById(documentIdentifier.getExtensionId())) {
            throw new NoSuchElementException("Extension not found: " + documentIdentifier.getExtensionId());
        }

        DocumentSlotData slot = DocumentSlotData.builder()
                .extensionId(documentIdentifier.getExtensionId())
                .slotKey(documentIdentifier.getSlotKey())
                .contentType(contentType)
                .maxSizeBytes(maxSizeBytes)
                .versioningEnabled(versioningEnabled)
                .maxVersionsRetained(maxVersionsRetained)
                .conflictStrategy(conflictStrategy)
                .encryptionRequired(encryptionRequired)
                .createdAt(Instant.now())
                .build();

        return documentSlotRepository.save(slot);
    }

    @Override
    @Auditing(action = AuditAction.DOCUMENT_SLOT_DELETED)
    public void delete(UUID extensionId, UUID slotId) {
        DocumentSlotData slot = documentSlotRepository.findById(slotId)
                .filter(s -> s.getExtensionId().equals(extensionId))
                .orElseThrow(() -> new NoSuchElementException("Document slot not found: " + slotId));

        documentSlotRepository.delete(slot);
    }
}