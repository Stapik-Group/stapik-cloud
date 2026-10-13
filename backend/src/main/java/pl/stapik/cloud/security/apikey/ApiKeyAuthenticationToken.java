package pl.stapik.cloud.security.apikey;

import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pl.stapik.cloud.admin.data.ApiKeyScope;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    public static final String WRITE_AUTHORITY = authorityName(ApiKeyScope.WRITE);

    private final transient ApiKeyPrincipal principal;

    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(ApiKeyPrincipal principal) {
        return List.of(new SimpleGrantedAuthority(authorityName(principal.scope())));
    }

    private static String authorityName(ApiKeyScope scope) {
        return "SCOPE_" + scope.name();
    }

    @Override
    public @Nullable Object getCredentials() {
        return null;
    }

    @Override
    public @Nullable Object getPrincipal() {
        return principal;
    }
}
