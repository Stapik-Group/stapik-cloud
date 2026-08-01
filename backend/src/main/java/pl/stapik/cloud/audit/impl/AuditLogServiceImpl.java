package pl.stapik.cloud.audit.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.audit.AuditLogRepository;
import pl.stapik.cloud.audit.AuditLogService;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.audit.dto.AuditLogEntryInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    @Override
    public void saveEntry(AuditLogEntryInfo auditLogEntryInfo) {
        AuditLogEntryData entry = AuditLogEntryData.builder()
                .extensionId(auditLogEntryInfo.getExtensionId())
                .actor(auditLogEntryInfo.getActor())
                .action(auditLogEntryInfo.getAction().name())
                .details(auditLogEntryInfo.getDetails())
                .occurredAt(Instant.now(clock))
                .build();

        auditLogRepository.save(entry);
    }

    @Override
    public Page<AuditLogEntryData> list(UUID extensionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return extensionId != null
                ? auditLogRepository.findByExtensionIdOrderByOccurredAtDesc(extensionId, pageable)
                : auditLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }
}