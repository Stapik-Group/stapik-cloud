package pl.stapik.cloud.config.logger;

import org.springframework.stereotype.Component;

@Component
public class TruncatedLoggerBuilder extends LoggerBuilder {

    private static final int CONSOLE_MAX_LENGTH = 1000;

    @Override
    public String createRequest(String method, String uri, String body) {
        return String.format("[REQUEST] %s %s | Payload: %s", method, uri, truncate(body));
    }

    @Override
    public String createResponse(String method, String uri, int status, long duration, String body) {
        return String.format("[RESPONSE] %s %s | Status: %d | Duration: %.4f ms | Payload: %s", method, uri, status, (double) duration, truncate(body));
    }

    private String truncate(String value) {
        if (value.length() <= CONSOLE_MAX_LENGTH) {
            return value;
        }

        return value.substring(0, CONSOLE_MAX_LENGTH)
                + "... [TRUNCATED]";
    }
}
