package pl.stapik.cloud.document.diff;

import com.fasterxml.jackson.databind.JsonNode;

public record JsonChange(String path, ChangeType type, String before, String after) {

    static JsonChange added(String path, JsonNode after) {
        return new JsonChange(path, ChangeType.ADDED, null, after.toString());
    }

    static JsonChange removed(String path, JsonNode before) {
        return new JsonChange(path, ChangeType.REMOVED, before.toString(), null);
    }

    static JsonChange changed(String path, JsonNode before, JsonNode after) {
        return new JsonChange(path, ChangeType.CHANGED, before.toString(), after.toString());
    }
}
