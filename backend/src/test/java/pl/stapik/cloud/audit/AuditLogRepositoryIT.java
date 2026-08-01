package pl.stapik.cloud.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AuditLogRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldFindByExtensionIdOrderByOccurredAtDesc() {
        // given
        ExtensionData extension = createExtension("ext-1");
        ExtensionData otherExtension = createExtension("ext-2");

        Instant now = Instant.now();
        createEntry(extension.getId(), "ACTION_OLD", now.minusSeconds(120));
        createEntry(extension.getId(), "ACTION_NEW", now.minusSeconds(10));
        createEntry(extension.getId(), "ACTION_MID", now.minusSeconds(60));
        createEntry(otherExtension.getId(), "ACTION_OTHER", now);

        // when
        Page<AuditLogEntryData> result = auditLogRepository.findByExtensionIdOrderByOccurredAtDesc(
                extension.getId(), PageRequest.of(0, 10));

        // then
        assertThat(result.getContent())
                .hasSize(3)
                .extracting(AuditLogEntryData::getAction)
                .containsExactly("ACTION_NEW", "ACTION_MID", "ACTION_OLD");
    }

    @Test
    void shouldReturnEmptyPageWhenExtensionHasNoEntries() {
        // given
        ExtensionData extension = createExtension("ext-empty");

        // when
        Page<AuditLogEntryData> result = auditLogRepository.findByExtensionIdOrderByOccurredAtDesc(
                extension.getId(), PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldRespectPageableForExtensionQuery() {
        // given
        ExtensionData extension = createExtension("ext-paged");
        Instant now = Instant.now();
        createEntry(extension.getId(), "ACTION_1", now.minusSeconds(30));
        createEntry(extension.getId(), "ACTION_2", now.minusSeconds(20));
        createEntry(extension.getId(), "ACTION_3", now.minusSeconds(10));

        // when
        Page<AuditLogEntryData> result = auditLogRepository.findByExtensionIdOrderByOccurredAtDesc(
                extension.getId(), PageRequest.of(0, 2));

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AuditLogEntryData::getAction)
                .containsExactly("ACTION_3", "ACTION_2");
    }

    @Test
    void shouldFindAllOrderedByOccurredAtDescAcrossExtensions() {
        // given
        ExtensionData extension1 = createExtension("ext-a");
        ExtensionData extension2 = createExtension("ext-b");
        Instant now = Instant.now();

        createEntry(extension1.getId(), "ACTION_A", now.minusSeconds(90));
        createEntry(extension2.getId(), "ACTION_B", now.minusSeconds(5));
        createEntry(null, "SYSTEM_ACTION", now.minusSeconds(45));

        // when
        Page<AuditLogEntryData> result = auditLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 10));

        // then
        assertThat(result.getContent())
                .extracting(AuditLogEntryData::getAction)
                .containsExactly("ACTION_B", "SYSTEM_ACTION", "ACTION_A");
    }

    @Test
    void shouldReturnEmptyPageWhenNoEntriesExistAtAll() {
        // when
        Page<AuditLogEntryData> result = auditLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    private void createEntry(UUID extensionId, String action, Instant occurredAt) {
        AuditLogEntryData entry = AuditLogEntryData.builder()
                .extensionId(extensionId)
                .actor("test-actor")
                .action(action)
                .details(null)
                .occurredAt(occurredAt)
                .build();
        auditLogRepository.save(entry);
    }

    private ExtensionData createExtension(String slug) {
        ExtensionData ext = new ExtensionData();
        ext.setSlug(slug);
        ext.setDisplayName("Test Extension");
        ext.setEnabled(true);
        ext.setCreatedAt(Instant.now());
        return extensionRepository.save(ext);
    }
}