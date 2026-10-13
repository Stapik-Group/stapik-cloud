package pl.stapik.cloud.config.logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class HttpBodyRedactor {

    static final String MASK = "***";
    static final String BODY_NOT_LOGGED = "BODY_NOT_LOGGED";

    private static final Pattern USER_CONTENT_URI = Pattern.compile("/(documents|assets)(/|$)");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "refreshtoken", "rawkey", "code", "challengetoken", "secret", "provisioninguri", "backupcodes", "qrcodesvg");

    private final ObjectMapper objectMapper;

    public String redact(String uri, String body) {
        if (body.isBlank()) {
            return body;
        }
        if (USER_CONTENT_URI.matcher(uri).find()) {
            return BODY_NOT_LOGGED;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            maskSensitiveFields(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(name -> {
                if (SENSITIVE_FIELDS.contains(name.toLowerCase(Locale.ROOT))) {
                    objectNode.put(name, MASK);
                } else {
                    maskSensitiveFields(objectNode.get(name));
                }
            });
        } else {
            node.forEach(this::maskSensitiveFields);
        }
    }
}
