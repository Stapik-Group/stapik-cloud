package pl.stapik.cloud.document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DailyWriteTotal;
import pl.stapik.cloud.document.dto.DocumentConflictSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersionData, UUID> {
    List<DocumentVersionData> findByDocumentIdOrderBySavedAtDesc(UUID documentId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM document_version
            WHERE document_id = :documentId
              AND id NOT IN (
                SELECT id FROM document_version
                WHERE document_id = :documentId
                ORDER BY saved_at DESC, id DESC
                LIMIT :versionsToKeep
              )
            """, nativeQuery = true)
    int deleteAllExceptNewest(@Param("documentId") UUID documentId, @Param("versionsToKeep") int versionsToKeep);

    @Query("""
            SELECT version.id AS versionId,
                   extension.id AS extensionId,
                   extension.displayName AS extensionName,
                   slot.slotKey AS slotKey,
                   document.partitionKey AS partitionKey,
                   version.deviceLabel AS deviceLabel,
                   version.savedAt AS savedAt
            FROM DocumentVersionData version, DocumentData document, DocumentSlotData slot, ExtensionData extension
            WHERE version.documentId = document.id
              AND document.documentSlotId = slot.id
              AND slot.extensionId = extension.id
              AND version.reason = :reason
            ORDER BY version.savedAt DESC
            """)
    List<DocumentConflictSummary> findRecentByReason(@Param("reason") VersionReason reason, Pageable pageable);

    @Query(value = """
            SELECT to_char(saved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day,
                   COUNT(*) AS total
            FROM document_version
            WHERE reason = 'NORMAL_WRITE'
              AND saved_at >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DailyWriteTotal> countWritesPerDaySince(@Param("since") Instant since);
}
