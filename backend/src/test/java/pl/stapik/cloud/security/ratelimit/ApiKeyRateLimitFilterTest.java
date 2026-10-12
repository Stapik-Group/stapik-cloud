package pl.stapik.cloud.security.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pl.stapik.cloud.admin.data.ApiKeyScope;
import pl.stapik.cloud.security.apikey.ApiKeyAuthenticationToken;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiKeyRateLimitFilterTest {

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectRequestsOfApiKeyOverTheLimit() throws Exception {
        ApiKeyRateLimitFilter filter = new ApiKeyRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);
        authenticateWithApiKey(UUID.randomUUID());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionResolver).resolveException(any(), any(), isNull(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void shouldLimitEachApiKeySeparately() throws Exception {
        ApiKeyRateLimitFilter filter = new ApiKeyRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);

        authenticateWithApiKey(UUID.randomUUID());
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);
        authenticateWithApiKey(UUID.randomUUID());
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void shouldPassThroughUnauthenticatedRequests() throws Exception {
        ApiKeyRateLimitFilter filter = new ApiKeyRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
    }

    private void authenticateWithApiKey(UUID apiKeyId) {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKeyId, UUID.randomUUID(), "label", ApiKeyScope.WRITE);
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(principal));
    }
}
