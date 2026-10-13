package pl.stapik.cloud.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.stapik.cloud.asset.data.AssetData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetData, UUID> {
    List<AssetData> findByDocumentSlotId(UUID documentSlotId);
    Optional<AssetData> findByDocumentSlotIdAndFilename(UUID documentSlotId, String filename);

    @Query("SELECT COALESCE(SUM(asset.sizeBytes), 0) FROM AssetData asset")
    long sumSizeBytes();
}