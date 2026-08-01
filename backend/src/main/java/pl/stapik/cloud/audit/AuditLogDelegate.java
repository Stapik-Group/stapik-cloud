package pl.stapik.cloud.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AuditLogApiDelegate;
import pl.stapik.cloud.admin.data.AuditLogEntryResponse;
import pl.stapik.cloud.admin.data.AuditLogPageResponse;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.audit.impl.AuditLogServiceImpl;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditLogDelegate implements AuditLogApiDelegate {

    private final AuditLogServiceImpl auditLogService;
    private final AuditLogMapper auditLogMapper;

    @Override
    public ResponseEntity<AuditLogPageResponse> listAuditLog(UUID extensionId, Integer page, Integer size) {
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 50;

        Page<AuditLogEntryData> result = auditLogService.list(extensionId, resolvedPage, resolvedSize);

        List<AuditLogEntryResponse> items = result.getContent().stream()
                .map(auditLogMapper::toResponse)
                .toList();

        AuditLogPageResponse response = new AuditLogPageResponse()
                .items(items)
                .page(resolvedPage)
                .size(resolvedSize)
                .totalElements(result.getTotalElements());

        return ResponseEntity.ok(response);
    }
}