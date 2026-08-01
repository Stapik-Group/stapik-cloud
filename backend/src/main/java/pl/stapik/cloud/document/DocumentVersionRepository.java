package pl.stapik.cloud.document;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.stapik.cloud.document.data.DocumentVersionData;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersionData, UUID> {
    List<DocumentVersionData> findByDocumentIdOrderBySavedAtDesc(UUID documentId);
}