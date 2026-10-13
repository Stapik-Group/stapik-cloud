package pl.stapik.cloud.document.dto;

import java.time.Instant;

public interface DocumentChangeSummary {
    String getSlotKey();
    String getPartitionKey();
    long getSizeBytes();
    String getContentHash();
    Instant getUpdatedAt();
    Instant getDeletedAt();
}
