package pl.stapik.cloud.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentChangeSummary;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentData, UUID> {
    Optional<DocumentData> findByDocumentSlotIdAndPartitionKey(UUID documentSlotId, String partitionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT document FROM DocumentData document
            WHERE document.documentSlotId = :documentSlotId
              AND document.partitionKey = :partitionKey
            """)
    Optional<DocumentData> findForUpdateByDocumentSlotIdAndPartitionKey(@Param("documentSlotId") UUID documentSlotId,
                                                                       @Param("partitionKey") String partitionKey);

    @Query("""
            SELECT document.partitionKey AS partitionKey,
                   document.sizeBytes AS sizeBytes,
                   document.contentHash AS contentHash,
                   document.updatedAt AS updatedAt
            FROM DocumentData document
            WHERE document.documentSlotId = :documentSlotId
              AND document.partitionKey <> ''
              AND document.deletedAt IS NULL
            ORDER BY document.partitionKey
            """)
    List<DocumentPartitionSummary> findPartitionSummaries(@Param("documentSlotId") UUID documentSlotId);

    @Query("""
            SELECT slot.slotKey AS slotKey,
                   document.partitionKey AS partitionKey,
                   document.sizeBytes AS sizeBytes,
                   document.contentHash AS contentHash,
                   document.updatedAt AS updatedAt,
                   document.deletedAt AS deletedAt
            FROM DocumentData document, DocumentSlotData slot
            WHERE document.documentSlotId = slot.id
              AND slot.extensionId = :extensionId
              AND document.updatedAt > :since
            ORDER BY document.updatedAt, slot.slotKey, document.partitionKey
            """)
    List<DocumentChangeSummary> findChanges(@Param("extensionId") UUID extensionId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(document.sizeBytes), 0) FROM DocumentData document WHERE document.deletedAt IS NULL")
    long sumActiveSizeBytes();

    long countByDeletedAtIsNull();
}
