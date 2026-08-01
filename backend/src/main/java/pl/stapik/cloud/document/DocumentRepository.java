package pl.stapik.cloud.document;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.stapik.cloud.document.data.DocumentData;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentData, UUID> {
    Optional<DocumentData> findByDocumentSlotId(UUID documentSlotId);
}