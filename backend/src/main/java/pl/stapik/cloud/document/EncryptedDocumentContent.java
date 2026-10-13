package pl.stapik.cloud.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class EncryptedDocumentContent {

    static final String FORMAT = "stapik-e2e";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EncryptedDocumentContent() {
    }

    public static boolean isEncrypted(String content) {
        try {
            JsonNode format = OBJECT_MAPPER.readTree(content).path("format");
            return format.isTextual() && FORMAT.equals(format.asText());
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
