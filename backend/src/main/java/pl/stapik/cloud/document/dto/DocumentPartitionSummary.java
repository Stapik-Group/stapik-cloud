package pl.stapik.cloud.document.dto;

import java.time.Instant;

public interface DocumentPartitionSummary {
    String getPartitionKey();
    long getSizeBytes();
    String getContentHash();
    Instant getUpdatedAt();
}
