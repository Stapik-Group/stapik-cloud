package pl.stapik.cloud.documentslot;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.SlotsApiDelegate;
import pl.stapik.cloud.admin.data.CreateDocumentSlotRequest;
import pl.stapik.cloud.admin.data.DocumentSlotListResponse;
import pl.stapik.cloud.admin.data.DocumentSlotResponse;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.impl.DocumentSlotServiceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentSlotDelegate implements SlotsApiDelegate {

    private static final long DEFAULT_MAX_SIZE_BYTES = 1_048_576L;
    private static final boolean DEFAULT_VERSIONING_ENABLED = true;
    private static final int DEFAULT_MAX_VERSIONS_RETAINED = 20;
    private static final ConflictStrategy DEFAULT_CONFLICT_STRATEGY = ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY;
    private static final boolean DEFAULT_ENCRYPTION_REQUIRED = false;

    private final DocumentSlotServiceImpl documentSlotServiceImpl;
    private final DocumentSlotMapper documentSlotMapper;

    @Override
    public ResponseEntity<DocumentSlotListResponse> listDocumentSlots(UUID extensionId) {
        List<DocumentSlotResponse> slots = documentSlotServiceImpl.listByExtension(extensionId).stream()
                .map(documentSlotMapper::toResponse)
                .toList();

        return ResponseEntity.ok(new DocumentSlotListResponse().slots(slots));
    }

    @Override
    public ResponseEntity<DocumentSlotResponse> createDocumentSlot(UUID extensionId, CreateDocumentSlotRequest request) {
        ContentType contentType = ContentType.valueOf(request.getContentType().getValue());

        long maxSizeBytes = Optional.ofNullable(request.getMaxSizeBytes()).orElse(DEFAULT_MAX_SIZE_BYTES);
        boolean versioningEnabled = Optional.ofNullable(request.getVersioningEnabled()).orElse(DEFAULT_VERSIONING_ENABLED);
        int maxVersionsRetained = Optional.ofNullable(request.getMaxVersionsRetained()).orElse(DEFAULT_MAX_VERSIONS_RETAINED);
        boolean encryptionRequired = Optional.ofNullable(request.getEncryptionRequired()).orElse(DEFAULT_ENCRYPTION_REQUIRED);

        ConflictStrategy conflictStrategy = Optional.ofNullable(request.getConflictStrategy()).isPresent()
                ? ConflictStrategy.valueOf(request.getConflictStrategy().getValue())
                : DEFAULT_CONFLICT_STRATEGY;

        DocumentSlotData created = documentSlotServiceImpl.create(
                DocumentIdentifier.of(extensionId, request.getSlotKey()),
                contentType,
                maxSizeBytes,
                versioningEnabled,
                maxVersionsRetained,
                conflictStrategy,
                encryptionRequired
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(documentSlotMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<Void> deleteDocumentSlot(UUID extensionId, UUID slotId) {
        documentSlotServiceImpl.delete(extensionId, slotId);
        return ResponseEntity.noContent().build();
    }
}