package pl.stapik.cloud.document.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DocumentIdentifier {
    private UUID extensionId;
    private String slotKey;

    public static DocumentIdentifier of(UUID extensionId, String slotKey) {
        return DocumentIdentifier.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .build();
    }
}
