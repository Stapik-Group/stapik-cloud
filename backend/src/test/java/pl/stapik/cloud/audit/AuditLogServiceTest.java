package pl.stapik.cloud.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.audit.dto.AuditLogEntryInfo;
import pl.stapik.cloud.audit.impl.AuditLogServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-07-26T22:45:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Test
    void shouldRecordEntryWithDetails() {
        // given
        UUID extensionId = UUID.randomUUID();

        // when
        AuditLogEntryInfo auditLogEntryInfo = AuditLogEntryInfo.of(extensionId, "admin@stapik.pl", AuditAction.EXTENSION_CREATED, "{\"slug\":\"test\"}");
        auditLogService.saveEntry(auditLogEntryInfo);

        // then
        ArgumentCaptor<AuditLogEntryData> captor = ArgumentCaptor.forClass(AuditLogEntryData.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntryData saved = captor.getValue();
        assertThat(saved.getExtensionId()).isEqualTo(extensionId);
        assertThat(saved.getActor()).isEqualTo("admin@stapik.pl");
        assertThat(saved.getAction()).isEqualTo("EXTENSION_CREATED");
        assertThat(saved.getDetails()).isEqualTo("{\"slug\":\"test\"}");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void shouldRecordEntryWithNullExtensionId() {
        // when
        AuditLogEntryInfo auditLogEntryInfo = AuditLogEntryInfo.of(null, "system", AuditAction.EXTENSION_CREATED, "startup");
        auditLogService.saveEntry(auditLogEntryInfo);

        // then
        ArgumentCaptor<AuditLogEntryData> captor = ArgumentCaptor.forClass(AuditLogEntryData.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getExtensionId()).isNull();
    }

    @Test
    void shouldRecordEntryWithoutDetailsUsingOverload() {
        // given
        UUID extensionId = UUID.randomUUID();

        // when
        AuditLogEntryInfo auditLogEntryInfo = AuditLogEntryInfo.of(extensionId, "admin@stapik.pl", AuditAction.EXTENSION_DELETED);
        auditLogService.saveEntry(auditLogEntryInfo);

        // then
        ArgumentCaptor<AuditLogEntryData> captor = ArgumentCaptor.forClass(AuditLogEntryData.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntryData saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("EXTENSION_DELETED");
        assertThat(saved.getDetails()).isNull();
    }

    @Test
    void shouldListByExtensionIdWhenProvided() {
        // given
        UUID extensionId = UUID.randomUUID();
        Page<AuditLogEntryData> page = new PageImpl<>(List.of(
                AuditLogEntryData.builder().id(UUID.randomUUID()).extensionId(extensionId).build()
        ));

        when(auditLogRepository.findByExtensionIdOrderByOccurredAtDesc(eq(extensionId), any(Pageable.class)))
                .thenReturn(page);

        // when
        Page<AuditLogEntryData> result = auditLogService.list(extensionId, 0, 20);

        // then
        assertThat(result).isEqualTo(page);
        verify(auditLogRepository).findByExtensionIdOrderByOccurredAtDesc(extensionId, PageRequest.of(0, 20));
        verify(auditLogRepository, never()).findAllByOrderByOccurredAtDesc(any());
    }

    @Test
    void shouldListAllWhenExtensionIdIsNull() {
        // given
        Page<AuditLogEntryData> page = new PageImpl<>(List.of(
                AuditLogEntryData.builder().id(UUID.randomUUID()).build()
        ));

        when(auditLogRepository.findAllByOrderByOccurredAtDesc(any(Pageable.class))).thenReturn(page);

        // when
        Page<AuditLogEntryData> result = auditLogService.list(null, 0, 20);

        // then
        assertThat(result).isEqualTo(page);
        verify(auditLogRepository).findAllByOrderByOccurredAtDesc(PageRequest.of(0, 20));
        verify(auditLogRepository, never()).findByExtensionIdOrderByOccurredAtDesc(any(), any());
    }

    @Test
    void shouldBuildCorrectPageableFromPageAndSize() {
        // given
        when(auditLogRepository.findAllByOrderByOccurredAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        auditLogService.list(null, 3, 15);

        // then
        verify(auditLogRepository).findAllByOrderByOccurredAtDesc(PageRequest.of(3, 15));
    }
}