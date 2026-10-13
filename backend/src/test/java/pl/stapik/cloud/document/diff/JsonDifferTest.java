package pl.stapik.cloud.document.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDifferTest {

    @Test
    void shouldReturnNoChangesForEquivalentDocuments() {
        // given
        String before = "{\"a\": 1, \"b\": [1, 2]}";
        String after = "{\"b\":[1,2],\"a\":1}";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).isEmpty();
    }

    @Test
    void shouldDetectAddedRemovedAndChangedFields() {
        // given
        String before = "{\"kept\": 1, \"removed\": true, \"changed\": \"old\"}";
        String after = "{\"kept\": 1, \"changed\": \"new\", \"added\": [1]}";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).containsExactly(
                new JsonChange("/removed", ChangeType.REMOVED, "true", null),
                new JsonChange("/changed", ChangeType.CHANGED, "\"old\"", "\"new\""),
                new JsonChange("/added", ChangeType.ADDED, null, "[1]")
        );
    }

    @Test
    void shouldDetectNestedChangesAndArrayElementChanges() {
        // given
        String before = "{\"events\": [{\"title\": \"a\"}, {\"title\": \"b\"}]}";
        String after = "{\"events\": [{\"title\": \"a\"}, {\"title\": \"c\"}, {\"title\": \"d\"}]}";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).containsExactly(
                new JsonChange("/events/1/title", ChangeType.CHANGED, "\"b\"", "\"c\""),
                new JsonChange("/events/2", ChangeType.ADDED, null, "{\"title\":\"d\"}")
        );
    }

    @Test
    void shouldEscapeSpecialCharactersInPointerTokens() {
        // given
        String before = "{\"a/b\": 1, \"c~d\": 1}";
        String after = "{\"a/b\": 2, \"c~d\": 2}";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).extracting(JsonChange::path).containsExactly("/a~1b", "/c~0d");
    }

    @Test
    void shouldReportRootChangeWhenTypesDiffer() {
        // given
        String before = "{\"a\": 1}";
        String after = "[1]";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).containsExactly(new JsonChange("", ChangeType.CHANGED, "{\"a\":1}", "[1]"));
    }

    @Test
    void shouldTreatInvalidJsonAsPlainText() {
        // given
        String before = "first line";
        String after = "second line";

        // when
        List<JsonChange> changes = JsonDiffer.diff(before, after);

        // then
        assertThat(changes).containsExactly(
                new JsonChange("", ChangeType.CHANGED, "\"first line\"", "\"second line\"")
        );
    }
}
