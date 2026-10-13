package pl.stapik.cloud.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pl.stapik.cloud.admin.AdminSessionService;
import pl.stapik.cloud.apikey.ApiKeyProperties;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.common.crypto.ApiKeyHasher;
import pl.stapik.cloud.extension.ExtensionRepository;
import pl.stapik.cloud.security.admin.JwtAuthenticationFilter;
import pl.stapik.cloud.security.admin.JwtService;
import pl.stapik.cloud.security.apikey.ApiKeyAuthenticationFilter;
import pl.stapik.cloud.security.apikey.ApiKeyAuthenticationToken;
import pl.stapik.cloud.security.ratelimit.ApiKeyRateLimitFilter;
import pl.stapik.cloud.security.ratelimit.KeyedRateLimiter;
import pl.stapik.cloud.security.ratelimit.LoginRateLimitFilter;
import pl.stapik.cloud.security.ratelimit.RateLimitProperties;
import pl.stapik.cloud.security.TransportSecurityProperties;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(TransportSecurityProperties.class)
public class SecurityConfig {

    private static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";
    private static final String DOCS_CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'";
    private static final Duration HSTS_MAX_AGE = Duration.ofDays(365);

    private final HandlerExceptionResolver exceptionResolver;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyRateLimitFilter apiKeyRateLimitFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final TransportSecurityProperties transportSecurityProperties;

    @Autowired
    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                          ApiKeyRepository apiKeyRepository,
                          ExtensionRepository extensionRepository,
                          ApiKeyHasher apiKeyHasher,
                          ApiKeyProperties apiKeyProperties,
                          Clock clock,
                          JwtService jwtService,
                          AdminSessionService adminSessionService,
                          RateLimitProperties rateLimitProperties,
                          TransportSecurityProperties transportSecurityProperties) {
        this.exceptionResolver = exceptionResolver;
        this.transportSecurityProperties = transportSecurityProperties;
        this.apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyRepository, extensionRepository, apiKeyHasher, apiKeyProperties, clock);
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, adminSessionService);
        this.apiKeyRateLimitFilter = new ApiKeyRateLimitFilter(
                new KeyedRateLimiter(rateLimitProperties.apiKey().requestsPerMinute()), exceptionResolver);
        this.loginRateLimitFilter = new LoginRateLimitFilter(
                new KeyedRateLimiter(rateLimitProperties.login().ipRequestsPerMinute()), exceptionResolver);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain applicationApiFilterChain(HttpSecurity http) {
        final String apiBaseAllPattern = "/api/v1/**";
        http
                .securityMatcher(apiBaseAllPattern)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> applySecurityHeaders(headers, API_CONTENT_SECURITY_POLICY))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, apiBaseAllPattern).authenticated()
                        .requestMatchers(HttpMethod.HEAD, apiBaseAllPattern).authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, apiBaseAllPattern).authenticated()
                        .anyRequest().hasAuthority(ApiKeyAuthenticationToken.WRITE_AUTHORITY))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyRateLimitFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminApiFilterChain(HttpSecurity http) {
        final String apiAdminAllPattern = "/api/admin/**";

        http
                .securityMatcher(apiAdminAllPattern)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> applySecurityHeaders(headers, API_CONTENT_SECURITY_POLICY))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/login/two-factor", "/api/admin/auth/refresh", "/api/admin/auth/logout").permitAll()
                        .requestMatchers("/api/admin/account/**").authenticated()
                        .requestMatchers(HttpMethod.GET, apiAdminAllPattern).authenticated()
                        .requestMatchers(HttpMethod.HEAD, apiAdminAllPattern).authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, apiAdminAllPattern).authenticated()
                        .anyRequest().hasRole("OWNER"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler()))
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain docsFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/openapi/**", "/webjars/**")
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> applySecurityHeaders(headers, DOCS_CONTENT_SECURITY_POLICY))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private void applySecurityHeaders(HeadersConfigurer<HttpSecurity> headers, String contentSecurityPolicy) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> {
                    if (transportSecurityProperties.forceHttps()) {
                        hsts.maxAgeInSeconds(HSTS_MAX_AGE.toSeconds()).includeSubDomains(true);
                    } else {
                        hsts.disable();
                    }
                });
    }

    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) ->
                exceptionResolver.resolveException(request, response, null, authException);
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                exceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
