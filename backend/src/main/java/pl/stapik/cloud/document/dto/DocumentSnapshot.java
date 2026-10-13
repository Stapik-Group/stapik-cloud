package pl.stapik.cloud.document.dto;

import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;

import java.time.Instant;
import java.util.UUID;

public record DocumentSnapshot(UUID versionId, Instant savedAt, String content) {

    public static DocumentSnapshot ofVersion(DocumentVersionData version) {
        return new DocumentSnapshot(version.getId(), version.getSavedAt(), version.getContent());
    }

    public static DocumentSnapshot ofCurrent(DocumentData document) {
        return new DocumentSnapshot(null, document.getUpdatedAt(), document.getContent());
    }
}
