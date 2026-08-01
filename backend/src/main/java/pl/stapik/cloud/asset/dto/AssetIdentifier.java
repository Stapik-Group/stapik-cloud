package pl.stapik.cloud.asset.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AssetIdentifier {
    private UUID extensionId;
    private String slotKey;
    private String filename;

    public static AssetIdentifier of(UUID extensionId, String slotKey) {
        return AssetIdentifier.of(extensionId, slotKey, null);
    }

    public static AssetIdentifier of(UUID extensionId, String slotKey, String filename) {
        return AssetIdentifier.builder()
                .extensionId(extensionId)
                .slotKey(slotKey)
                .filename(filename)
                .build();
    }
}
