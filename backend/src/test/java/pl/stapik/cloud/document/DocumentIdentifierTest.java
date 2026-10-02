package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentIdentifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIdentifierTest {

    private static final UUID EXTENSION_ID = UUID.randomUUID();
    private static final String SLOT_KEY = "calendar.json";

    @Test
    void shouldUseDefaultPartitionWhenNoneIsGiven() {
        // when
        DocumentIdentifier identifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY);

        // then
        assertThat(identifier.getPartitionKey()).isEqualTo(DocumentData.DEFAULT_PARTITION_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024", "archive-2024", "q1_2024", "v1.2", "A", "a123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"})
    void shouldAcceptValidPartitionKeys(String partitionKey) {
        // when
        DocumentIdentifier identifier = DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY, partitionKey);

        // then
        assertThat(identifier.getPartitionKey()).isEqualTo(partitionKey);
        assertThat(identifier.getSlotKey()).isEqualTo(SLOT_KEY);
        assertThat(identifier.getExtensionId()).isEqualTo(EXTENSION_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", ".hidden", "-leading-dash", "_leading", "a/b", "a\\b", "a b", "żółć", "2024?x=1",
            "a1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"})
    void shouldRejectInvalidPartitionKeys(String partitionKey) {
        // when / then
        assertThatThrownBy(() -> DocumentIdentifier.of(EXTENSION_ID, SLOT_KEY, partitionKey))
                .isInstanceOf(InvalidPartitionKeyException.class);
    }
}
