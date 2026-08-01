package pl.stapik.cloud.documentslot;

import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.util.List;
import java.util.UUID;

public interface DocumentSlotService {
    List<DocumentSlotData> listByExtension(UUID extensionId);

    DocumentSlotData create(
            DocumentIdentifier documentIdentifier,
            ContentType contentType,
            long maxSizeBytes,
            boolean versioningEnabled,
            int maxVersionsRetained,
            ConflictStrategy conflictStrategy,
            boolean encryptionRequired
    );

    void delete(UUID extensionId, UUID slotId);
}
