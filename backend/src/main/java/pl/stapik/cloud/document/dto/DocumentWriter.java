package pl.stapik.cloud.document.dto;

import java.util.UUID;

public record DocumentWriter(UUID deviceId, String deviceLabel) {
    public static final DocumentWriter NONE = new DocumentWriter(null, null);
}
