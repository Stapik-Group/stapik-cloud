package pl.stapik.cloud.security.admin;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentAdmin {

    public UUID id() {
        return principal().adminUserId();
    }

    public UUID sessionId() {
        return principal().sessionId();
    }

    private JwtPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal;
        }
        throw new AuthenticationCredentialsNotFoundException("No signed-in admin");
    }
}
