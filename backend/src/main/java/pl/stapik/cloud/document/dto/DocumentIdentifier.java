package pl.stapik.cloud.document.dto;

import lombok.Builder;
import lombok.Data;
import pl.stapik.cloud.document.InvalidPartitionKeyException;
import pl.stapik.cloud.document.data.DocumentData;

import java.util.UUID;
import java.util.regex.Pattern;

@Data
@Builder
public class DocumentIdentifier {
    private static final Pattern PARTITION_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");

    private UUID extensionId;
    private String slotKey;
    @Builder.Default
    private String partitionKey = DocumentData.DEFAULT_PARTITION_KEY;

    public static DocumentIdentifier of(UUID extensionId, String slotKey) {
        return DocumentIdentifier.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .build();
    }

    public static DocumentIdentifier of(UUID extensionId, String slotKey, String partitionKey) {
        if (!PARTITION_KEY_PATTERN.matcher(partitionKey).matches()) {
            throw new InvalidPartitionKeyException(partitionKey);
        }

        return DocumentIdentifier.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .partitionKey(partitionKey)
                .build();
    }
}
