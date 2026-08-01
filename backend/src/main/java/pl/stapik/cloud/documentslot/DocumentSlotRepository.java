package pl.stapik.cloud.documentslot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentSlotRepository extends JpaRepository<DocumentSlotData, UUID> {
    Optional<DocumentSlotData> findByExtensionIdAndSlotKey(UUID extensionId, String slotKey);
    List<DocumentSlotData> findByExtensionId(UUID extensionId);
}