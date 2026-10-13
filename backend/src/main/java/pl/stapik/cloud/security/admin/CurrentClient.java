package pl.stapik.cloud.security.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentClient {

    private static final int MAX_IP_ADDRESS_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    public ClientInfo describe() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return new ClientInfo(
                    truncate(request.getRemoteAddr(), MAX_IP_ADDRESS_LENGTH),
                    truncate(request.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT_LENGTH));
        }
        return new ClientInfo(null, null);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
