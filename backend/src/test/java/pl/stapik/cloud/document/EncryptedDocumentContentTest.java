package pl.stapik.cloud.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedDocumentContentTest {

    @Test
    void shouldRecognizeEncryptedEnvelope() {
        String envelope = """
                {"format":"stapik-e2e","version":1,"salt":"c2FsdA==","nonce":"bm9uY2U=","ciphertext":"Y2lwaGVy"}""";

        assertThat(EncryptedDocumentContent.isEncrypted(envelope)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"foo\": \"bar\"}",
            "{\"format\": \"other\"}",
            "{\"format\": 1}",
            "{\"nested\": {\"format\": \"stapik-e2e\"}}",
            "[{\"format\": \"stapik-e2e\"}]",
            "\"stapik-e2e\"",
            "plain text",
            "{broken",
            ""
    })
    void shouldNotRecognizeOtherContentAsEncrypted(String content) {
        assertThat(EncryptedDocumentContent.isEncrypted(content)).isFalse();
    }
}
