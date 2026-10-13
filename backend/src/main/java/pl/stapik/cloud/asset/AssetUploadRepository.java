package pl.stapik.cloud.asset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.asset.data.AssetUploadData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetUploadRepository extends JpaRepository<AssetUploadData, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT upload FROM AssetUploadData upload WHERE upload.id = :id")
    Optional<AssetUploadData> findForUpdateById(@Param("id") UUID id);

    List<AssetUploadData> findByExpiresAtBefore(Instant threshold);
}
