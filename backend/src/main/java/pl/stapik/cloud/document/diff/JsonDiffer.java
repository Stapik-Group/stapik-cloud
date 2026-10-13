package pl.stapik.cloud.document.diff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JsonDiffer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonDiffer() {
    }

    public static List<JsonChange> diff(String before, String after) {
        List<JsonChange> changes = new ArrayList<>();
        collectChanges("", parse(before), parse(after), changes);
        return changes;
    }

    private static JsonNode parse(String content) {
        try {
            JsonNode tree = OBJECT_MAPPER.readTree(content);
            return tree.isMissingNode() ? TextNode.valueOf(content) : tree;
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(content);
        }
    }

    private static void collectChanges(String path, JsonNode before, JsonNode after, List<JsonChange> changes) {
        if (before.isObject() && after.isObject()) {
            collectObjectChanges(path, before, after, changes);
        } else if (before.isArray() && after.isArray()) {
            collectArrayChanges(path, before, after, changes);
        } else if (!before.equals(after)) {
            changes.add(JsonChange.changed(path, before, after));
        }
    }

    private static void collectObjectChanges(String path, JsonNode before, JsonNode after, List<JsonChange> changes) {
        Set<String> fieldNames = new LinkedHashSet<>();
        before.fieldNames().forEachRemaining(fieldNames::add);
        after.fieldNames().forEachRemaining(fieldNames::add);

        for (String fieldName : fieldNames) {
            collectChildChanges(path + "/" + escapePointerToken(fieldName), before.get(fieldName), after.get(fieldName), changes);
        }
    }

    private static void collectArrayChanges(String path, JsonNode before, JsonNode after, List<JsonChange> changes) {
        int length = Math.max(before.size(), after.size());

        for (int index = 0; index < length; index++) {
            collectChildChanges(path + "/" + index, before.get(index), after.get(index), changes);
        }
    }

    private static void collectChildChanges(String path, JsonNode before, JsonNode after, List<JsonChange> changes) {
        if (before == null) {
            changes.add(JsonChange.added(path, after));
        } else if (after == null) {
            changes.add(JsonChange.removed(path, before));
        } else {
            collectChanges(path, before, after, changes);
        }
    }

    private static String escapePointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
