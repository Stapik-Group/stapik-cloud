package pl.stapik.cloud.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.document.data.DocumentVersionData;

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
}