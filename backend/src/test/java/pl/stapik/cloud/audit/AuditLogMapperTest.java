package pl.stapik.cloud.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import pl.stapik.cloud.admin.data.AuditLogEntryResponse;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.common.mapper.DateTimeMapperImpl;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuditLogMapperTest {

    @Spy
    private DateTimeMapperImpl dateTimeMapper;

    @Spy
    private JsonNullableMapper jsonNullableMapper;

    @InjectMocks
    private AuditLogMapperImpl auditLogMapper;

    @Test
    void shouldMapEntryToResponse() {
        // Given
        UUID id = UUID.randomUUID();
        UUID extensionId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-18T10:00:00Z");

        AuditLogEntryData entry = AuditLogEntryData.builder()
                .id(id)
                .extensionId(extensionId)
                .actor("admin@stapik.pl")
                .action("EXTENSION_CREATED")
                .details("{\"slug\":\"test-ext\"}")
                .occurredAt(occurredAt)
                .build();

        // When
        AuditLogEntryResponse response = auditLogMapper.toResponse(entry);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getExtensionId()).isEqualTo(JsonNullable.of(extensionId));
        assertThat(response.getAction()).isEqualTo("EXTENSION_CREATED");
        assertThat(response.getDetails()).isEqualTo(JsonNullable.of("{\"slug\":\"test-ext\"}"));
        assertThat(response.getOccurredAt()).isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldWrapNullExtensionIdAndDetailsInJsonNullable() {
        // Given
        AuditLogEntryData entry = AuditLogEntryData.builder()
                .id(UUID.randomUUID())
                .extensionId(null)
                .actor("system")
                .action("SYSTEM_STARTUP")
                .details(null)
                .occurredAt(Instant.parse("2026-07-18T10:00:00Z"))
                .build();

        // When
        AuditLogEntryResponse response = auditLogMapper.toResponse(entry);

        // Then
        assertThat(response.getExtensionId()).isEqualTo(JsonNullable.of(null));
        assertThat(response.getDetails()).isEqualTo(JsonNullable.of(null));
    }

    @Test
    void shouldReturnNullWhenMappingNullEntry() {
        // When
        AuditLogEntryResponse response = auditLogMapper.toResponse(null);

        // Then
        assertThat(response).isNull();
    }
}