package pl.stapik.cloud.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.stapik.cloud.audit.data.AuditLogEntryData;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntryData, UUID> {
    Page<AuditLogEntryData> findByExtensionIdOrderByOccurredAtDesc(UUID extensionId, Pageable pageable);
    Page<AuditLogEntryData> findAllByOrderByOccurredAtDesc(Pageable pageable);
}