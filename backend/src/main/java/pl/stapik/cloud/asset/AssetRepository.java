package pl.stapik.cloud.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.stapik.cloud.asset.data.AssetData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetData, UUID> {
    List<AssetData> findByDocumentSlotId(UUID documentSlotId);
    Optional<AssetData> findByDocumentSlotIdAndFilename(UUID documentSlotId, String filename);
}