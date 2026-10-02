package pl.stapik.cloud.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentData, UUID> {
    Optional<DocumentData> findByDocumentSlotIdAndPartitionKey(UUID documentSlotId, String partitionKey);

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
}