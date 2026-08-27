package pl.stapik.cloud.config.logger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JsonLoggerBuilder extends LoggerBuilder {

    private final ObjectMapper objectMapper;

    @Override
    public String createRequest(String method, String uri, String body) {
        var httpLogEntry = new JsonLoggerBuilder.HttpLogEntry(Instant.now(), "REQUEST", MDC.get("traceId"), method, uri, null, null, parsePayload(body));
        return toJson(httpLogEntry);
    }

    @Override
    public String createResponse(String method, String uri, int status, long duration, String body) {
        var httpLogEntry = new JsonLoggerBuilder.HttpLogEntry(Instant.now(), "RESPONSE", MDC.get("traceId"), method, uri, status, duration, parsePayload(body));
        return toJson(httpLogEntry);
    }

    private Object parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return payload;
        }
    }

    private String toJson(HttpLogEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            return "{\"loggingError\":\"Failed to serialize HTTP log\"}";
        }
    }

    private record HttpLogEntry(@JsonProperty("@timestamp") Instant timestamp, String type, String traceId,
                                String method, String uri, Integer status, Long durationMs, Object payload) { }
}