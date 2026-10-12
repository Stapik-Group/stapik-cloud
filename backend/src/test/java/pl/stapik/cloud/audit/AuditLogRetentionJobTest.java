package pl.stapik.cloud.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogRetentionJobTest {

    @Mock
    private AuditLogService auditLogService;

    @Test
    void shouldDeleteEntriesOlderThanConfiguredRetention() {
        // given
        AuditLogRetentionJob job = new AuditLogRetentionJob(auditLogService, 30);

        // when
        job.purgeExpiredEntries();

        // then
        verify(auditLogService).deleteEntriesOlderThan(Duration.ofDays(30));
    }
}
