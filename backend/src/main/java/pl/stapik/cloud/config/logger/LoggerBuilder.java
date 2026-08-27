package pl.stapik.cloud.config.logger;

public abstract class LoggerBuilder {
    public abstract String createRequest(String method, String uri, String body);
    public abstract String createResponse(String method, String uri, int status, long duration, String body);
}
