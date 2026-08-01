package pl.stapik.cloud.audit.dto;

import lombok.Builder;
import lombok.Data;
import pl.stapik.cloud.audit.data.AuditAction;

import java.util.UUID;

@Data
@Builder
public class AuditLogEntryInfo {
    private UUID extensionId;
    private String actor;
    private AuditAction action;
    private String details;

    public static AuditLogEntryInfo of(UUID extensionId, String actor, AuditAction action, String details) {
        return AuditLogEntryInfo.builder()
                .extensionId(extensionId)
                .actor(actor)
                .action(action)
                .details(details)
                .build();
    }

    public static AuditLogEntryInfo of(UUID extensionId, String actor, AuditAction action) {
        return AuditLogEntryInfo.of(extensionId, actor, action, null);
    }
}
