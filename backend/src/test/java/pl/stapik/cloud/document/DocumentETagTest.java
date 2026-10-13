package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentETagTest {

    @Test
    void shouldFormatContentHashAsQuotedETag() {
        assertThat(DocumentETag.format("abc")).isEqualTo("\"abc\"");
    }

    @Test
    void shouldParseQuotedETag() {
        assertThat(DocumentETag.parse("\"abc\"")).isEqualTo("abc");
    }

    @Test
    void shouldRoundTripFormattedETag() {
        assertThat(DocumentETag.parse(DocumentETag.format("abc"))).isEqualTo("abc");
    }

    @Test
    void shouldReturnNullForMissingHeader() {
        assertThat(DocumentETag.parse(null)).isNull();
    }

    @Test
    void shouldReturnNullForUnquotedValue() {
        assertThat(DocumentETag.parse("abc")).isNull();
    }
}
