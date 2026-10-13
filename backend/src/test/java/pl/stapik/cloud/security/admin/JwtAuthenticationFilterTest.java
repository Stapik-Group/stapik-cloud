package pl.stapik.cloud.security.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.stapik.cloud.admin.AdminSessionService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token";

    @Mock
    private JwtService jwtService;

    @Mock
    private AdminSessionService adminSessionService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWhenSessionIsActive() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        given(jwtService.validate(TOKEN)).willReturn(Optional.of(principal(sessionId)));
        given(adminSessionService.isActive(sessionId)).willReturn(true);

        // when
        runFilter();

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void shouldNotAuthenticateWhenSessionWasRevoked() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        given(jwtService.validate(TOKEN)).willReturn(Optional.of(principal(sessionId)));
        given(adminSessionService.isActive(sessionId)).willReturn(false);

        // when
        runFilter();

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateTokenIssuedWithoutSession() throws Exception {
        // given
        given(jwtService.validate(TOKEN)).willReturn(Optional.of(principal(null)));

        // when
        runFilter();

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private void runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        new JwtAuthenticationFilter(jwtService, adminSessionService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private JwtPrincipal principal(UUID sessionId) {
        return new JwtPrincipal(UUID.randomUUID(), "owner", "OWNER", sessionId);
    }
}
