package pl.stapik.cloud.audit;

import org.springframework.data.domain.Page;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.audit.dto.AuditLogEntryInfo;

import java.util.UUID;

public interface AuditLogService {
    void saveEntry(AuditLogEntryInfo auditLogEntryInfo);
    Page<AuditLogEntryData> list(UUID extensionId, int page, int size);
}
