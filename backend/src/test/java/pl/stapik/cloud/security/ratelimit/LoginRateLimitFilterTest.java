package pl.stapik.cloud.security.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitFilterTest {

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    @Mock
    private FilterChain filterChain;

    @Test
    void shouldRejectLoginRequestsOverTheLimit() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);

        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionResolver).resolveException(any(), any(), isNull(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void shouldLimitEachClientAddressSeparately() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);

        filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(loginRequest("10.0.0.2"), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void shouldNotLimitOtherEndpoints() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new KeyedRateLimiter(1), exceptionResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/extensions");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
    }

    private MockHttpServletRequest loginRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
