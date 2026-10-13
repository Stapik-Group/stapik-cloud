package pl.stapik.cloud.document.dto;

import java.time.Instant;
import java.util.UUID;

public interface DocumentConflictSummary {
    UUID getVersionId();
    UUID getExtensionId();
    String getExtensionName();
    String getSlotKey();
    String getPartitionKey();
    String getDeviceLabel();
    Instant getSavedAt();
}
