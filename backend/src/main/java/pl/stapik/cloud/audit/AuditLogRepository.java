package pl.stapik.cloud.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.stapik.cloud.audit.data.AuditLogEntryData;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntryData, UUID> {
    Page<AuditLogEntryData> findByExtensionIdOrderByOccurredAtDesc(UUID extensionId, Pageable pageable);
    Page<AuditLogEntryData> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLogEntryData entry WHERE entry.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}