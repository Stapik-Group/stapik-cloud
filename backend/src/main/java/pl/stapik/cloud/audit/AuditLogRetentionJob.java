package pl.stapik.cloud.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class AuditLogRetentionJob {

    private final AuditLogService auditLogService;
    private final Duration retention;

    public AuditLogRetentionJob(AuditLogService auditLogService,
                                @Value("${stapik-cloud.audit.retention-days}") long retentionDays) {
        this.auditLogService = auditLogService;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredEntries() {
        int deleted = auditLogService.deleteEntriesOlderThan(retention);
        log.info("Deleted {} audit log entries older than {} days", deleted, retention.toDays());
    }
}
