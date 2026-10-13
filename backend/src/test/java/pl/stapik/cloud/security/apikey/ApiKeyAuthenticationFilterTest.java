package pl.stapik.cloud.security.apikey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.stapik.cloud.apikey.ApiKeyProperties;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.common.crypto.ApiKeyHasher;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-26T22:45:00Z");
    private static final String RAW_KEY = "prefix01-secret";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ExtensionRepository extensionRepository;

    @Mock
    private ApiKeyHasher apiKeyHasher;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository,
                extensionRepository,
                apiKeyHasher,
                new ApiKeyProperties(Duration.ofDays(365), Duration.ofDays(30)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAddExpiryHeaderWhenKeyExpiresWithinWarningPeriod() throws Exception {
        // given
        Instant expiresAt = NOW.plus(Duration.ofDays(10));
        givenValidKey(expiresAt);

        // when
        MockHttpServletResponse response = doFilter();

        // then
        assertThat(response.getHeader(ApiKeyAuthenticationFilter.EXPIRES_AT_HEADER_NAME)).isEqualTo(expiresAt.toString());
    }

    @Test
    void shouldNotAddExpiryHeaderWhenKeyExpiresAfterWarningPeriod() throws Exception {
        // given
        givenValidKey(NOW.plus(Duration.ofDays(200)));

        // when
        MockHttpServletResponse response = doFilter();

        // then
        assertThat(response.getHeader(ApiKeyAuthenticationFilter.EXPIRES_AT_HEADER_NAME)).isNull();
    }

    @Test
    void shouldNotAddExpiryHeaderWhenKeyNeverExpires() throws Exception {
        // given
        givenValidKey(null);

        // when
        MockHttpServletResponse response = doFilter();

        // then
        assertThat(response.getHeader(ApiKeyAuthenticationFilter.EXPIRES_AT_HEADER_NAME)).isNull();
    }

    private void givenValidKey(Instant expiresAt) {
        ApiKeyData apiKey = ApiKeyData.builder()
                .id(UUID.randomUUID())
                .extensionId(UUID.randomUUID())
                .label("key")
                .scope(ApiKeyScope.READ_ONLY)
                .hashedKey("hash")
                .expiresAt(expiresAt)
                .lastUsedAt(NOW)
                .build();
        given(apiKeyRepository.findByKeyPrefixAndRevokedFalse("prefix01")).willReturn(Optional.of(apiKey));
        given(apiKeyHasher.matches(RAW_KEY, "hash")).willReturn(true);
        given(extensionRepository.existsByIdAndEnabledTrue(any())).willReturn(true);
    }

    private MockHttpServletResponse doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-api-key", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
