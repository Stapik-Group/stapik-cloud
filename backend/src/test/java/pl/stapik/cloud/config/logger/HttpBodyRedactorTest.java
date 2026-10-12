package pl.stapik.cloud.config.logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBodyRedactorTest {

    private final HttpBodyRedactor redactor = new HttpBodyRedactor(new ObjectMapper());

    @Test
    void shouldMaskPasswordInLoginRequest() {
        String result = redactor.redact("/api/admin/auth/login", "{\"username\":\"admin\",\"password\":\"secret\"}");

        assertThat(result).isEqualTo("{\"username\":\"admin\",\"password\":\"***\"}");
    }

    @Test
    void shouldMaskTokensInLoginResponse() {
        String body = "{\"token\":\"jwt\",\"expiresAt\":\"2026-01-01T00:00:00Z\",\"refreshToken\":\"refresh\"}";

        String result = redactor.redact("/api/admin/auth/login", body);

        assertThat(result).isEqualTo("{\"token\":\"***\",\"expiresAt\":\"2026-01-01T00:00:00Z\",\"refreshToken\":\"***\"}");
    }

    @Test
    void shouldMaskRawKeyInNestedObject() {
        String result = redactor.redact("/api/admin/extensions/1/keys", "{\"key\":{\"label\":\"a\"},\"rawKey\":\"abc\"}");

        assertThat(result).isEqualTo("{\"key\":{\"label\":\"a\"},\"rawKey\":\"***\"}");
    }

    @Test
    void shouldMaskSensitiveFieldsInsideArrays() {
        String result = redactor.redact("/api/admin/x", "[{\"password\":\"a\"},{\"password\":\"b\"}]");

        assertThat(result).isEqualTo("[{\"password\":\"***\"},{\"password\":\"***\"}]");
    }

    @Test
    void shouldNotLogBodyForDocumentEndpoints() {
        assertThat(redactor.redact("/api/v1/documents/settings", "{\"a\":1}")).isEqualTo("BODY_NOT_LOGGED");
        assertThat(redactor.redact("/api/admin/extensions/1/documents/settings/versions", "[]")).isEqualTo("BODY_NOT_LOGGED");
    }

    @Test
    void shouldNotLogBodyForAssetEndpoints() {
        assertThat(redactor.redact("/api/v1/assets/slot/file.png", "binary")).isEqualTo("BODY_NOT_LOGGED");
    }

    @Test
    void shouldLeaveNonJsonBodyUntouched() {
        assertThat(redactor.redact("/api/v1/health", "plain text")).isEqualTo("plain text");
    }

    @Test
    void shouldLeaveEmptyBodyUntouched() {
        assertThat(redactor.redact("/api/admin/auth/login", "")).isEmpty();
    }
}
