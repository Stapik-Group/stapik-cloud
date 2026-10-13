package pl.stapik.cloud.security;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.common.crypto.ApiKeyHasher;
import pl.stapik.cloud.common.crypto.HashingService;
import pl.stapik.cloud.security.admin.JwtService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityE2ETest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HashingService hashingService;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private JwtService jwtService;

    @Value("${stapik-cloud.security.jwt.secret}")
    private String jwtSecret;

    private UUID extensionId;

    @BeforeEach
    void setUp() throws Exception {
        extensionId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM api_key");
            connection.createStatement().execute("DELETE FROM admin_user");
            connection.createStatement().execute("DELETE FROM extension");
            insertExtension(connection, extensionId);
        }
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void meWithoutApiKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithUnknownApiKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("x-api-key", "totally-unknown-key-value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidApiKeyReturnsPrincipal() throws Exception {
        String rawKey = insertApiKey(extensionId, "desktop-key", "READ_WRITE", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensionId").value(extensionId.toString()))
                .andExpect(jsonPath("$.keyLabel").value("desktop-key"))
                .andExpect(jsonPath("$.scope").value("READ_WRITE"));
    }

    @Test
    void meWithValidApiKeyAndBearerTokenAuthenticatesByApiKey() throws Exception {
        String rawKey = insertApiKey(extensionId, "desktop-key", "READ_WRITE", null, false);
        String adminToken = jwtService.generateToken(UUID.randomUUID(), "security-admin-5", "OWNER");

        mockMvc.perform(get("/api/v1/me")
                        .header("x-api-key", rawKey)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensionId").value(extensionId.toString()))
                .andExpect(jsonPath("$.keyLabel").value("desktop-key"));
    }

    @Test
    void meWithRevokedApiKeyIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "revoked-key", "READ_ONLY", null, true);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredApiKeyIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "expired-key", "READ_ONLY", Instant.now().minus(1, ChronoUnit.DAYS), false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyApiKeyCanReadButCannotWrite() throws Exception {
        String rawKey = insertApiKey(extensionId, "read-only-key", "READ_ONLY", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/documents/settings").header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/documents/settings").header("x-api-key", rawKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/documents/settings/versions/" + UUID.randomUUID() + "/restore")
                        .header("x-api-key", rawKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void readWriteApiKeyPassesWriteAuthorization() throws Exception {
        String rawKey = insertApiKey(extensionId, "read-write-key", "READ_WRITE", null, false);

        mockMvc.perform(delete("/api/v1/documents/settings").header("x-api-key", rawKey))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void viewerAdminCanReadButCannotWrite() throws Exception {
        String viewerToken = jwtService.generateToken(UUID.randomUUID(), "viewer", "VIEWER");

        mockMvc.perform(get("/api/admin/extensions").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/extensions/" + extensionId + "/keys")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/extensions/" + extensionId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerAdminPassesWriteAuthorization() throws Exception {
        String ownerToken = jwtService.generateToken(UUID.randomUUID(), "owner", "OWNER");

        mockMvc.perform(delete("/api/admin/extensions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void apiKeyOfDisabledExtensionIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "desktop-key", "READ_WRITE", null, false);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("UPDATE extension SET enabled = FALSE");
        }

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyBcryptApiKeyAuthenticatesAndIsUpgradedToSha256() throws Exception {
        String rawKey = "legacy_" + UUID.randomUUID();
        insertApiKeyRow(rawKey, hashingService.hash(rawKey), extensionId, "legacy-key", "READ_WRITE", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk());

        assertThat(queryApiKeyColumn("hashed_key")).isEqualTo(apiKeyHasher.hash(rawKey));
        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk());
    }

    @Test
    void lastUsedAtIsUpdatedAtMostOncePerMinute() throws Exception {
        String rawKey = insertApiKey(extensionId, "desktop-key", "READ_WRITE", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey)).andExpect(status().isOk());
        String firstUse = queryApiKeyColumn("last_used_at");
        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey)).andExpect(status().isOk());

        assertThat(firstUse).isNotNull();
        assertThat(queryApiKeyColumn("last_used_at")).isEqualTo(firstUse);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        insertAdminUser("security-admin");

        String payload = """
                {
                    "username": "security-admin",
                    "password": "correct-password"
                }
                """;

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.token").isNotEmpty())
                .andExpect(jsonPath("$.session.expiresAt").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        insertAdminUser("security-admin-2");

        String payload = """
                {
                    "username": "security-admin-2",
                    "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsLockedAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        insertAdminUser("lockout-admin");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload("lockout-admin", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("lockout-admin", "correct-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void successfulLoginResetsFailedAttempts() throws Exception {
        insertAdminUser("reset-admin");

        for (int round = 0; round < 3; round++) {
            for (int attempt = 0; attempt < 4; attempt++) {
                mockMvc.perform(post("/api/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginPayload("reset-admin", "wrong-password")))
                        .andExpect(status().isUnauthorized());
            }
            mockMvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload("reset-admin", "correct-password")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adminEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/extensions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointWithGarbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/extensions")
                        .header("Authorization", "Bearer this-is-not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointWithExpiredTokenIsRejected() throws Exception {
        String expiredToken = buildExpiredToken();

        mockMvc.perform(get("/api/admin/extensions")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenAccessAdminEndpointSucceeds() throws Exception {
        insertAdminUser("security-admin-4");

        String loginPayload = """
                {
                    "username": "security-admin-4",
                    "password": "correct-password"
                }
                """;

        String loginResponse = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(loginResponse, "$.session.token");

        mockMvc.perform(get("/api/admin/extensions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String queryApiKeyColumn(String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var resultSet = connection.createStatement().executeQuery("SELECT " + column + " FROM api_key")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private String loginPayload(String username, String password) {
        return """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);
    }

    private void insertExtension(Connection connection, UUID id) throws Exception {
        String sql = "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, "security-e2e-slug");
            ps.setString(3, "Security E2E Extension");
            ps.setBoolean(4, true);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private String insertApiKey(UUID extId, String label, String scope, Instant expiresAt, boolean revoked) throws Exception {
        String rawKey = "prefix_" + UUID.randomUUID();
        insertApiKeyRow(rawKey, apiKeyHasher.hash(rawKey), extId, label, scope, expiresAt, revoked);
        return rawKey;
    }

    private void insertApiKeyRow(String rawKey, String hashedKey, UUID extId, String label, String scope, Instant expiresAt, boolean revoked) throws Exception {
        String prefix = rawKey.substring(0, 8);

        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO api_key (id, extension_id, label, key_prefix, hashed_key, scope, expires_at, revoked, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extId);
                ps.setString(3, label);
                ps.setString(4, prefix);
                ps.setString(5, hashedKey);
                ps.setString(6, scope);
                if (expiresAt != null) {
                    ps.setTimestamp(7, Timestamp.from(expiresAt));
                } else {
                    ps.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                ps.setBoolean(8, revoked);
                ps.setTimestamp(9, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertAdminUser(String username) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO admin_user (id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, username);
                ps.setString(3, hashingService.hash("correct-password"));
                ps.setString(4, "OWNER");
                ps.setTimestamp(5, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private String buildExpiredToken() {
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Instant now = Instant.now();

        return Jwts.builder()
                .subject("security-admin-3")
                .id(UUID.randomUUID().toString())
                .claim("role", "OWNER")
                .issuer("stapik-cloud")
                .issuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}

