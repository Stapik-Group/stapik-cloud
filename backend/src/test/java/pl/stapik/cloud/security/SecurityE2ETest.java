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
import pl.stapik.cloud.common.crypto.HashingService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        String rawKey = insertApiKey(extensionId, "desktop-key", "READ_WRITE", null, null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensionId").value(extensionId.toString()))
                .andExpect(jsonPath("$.keyLabel").value("desktop-key"))
                .andExpect(jsonPath("$.scope").value("READ_WRITE"));
    }

    @Test
    void meWithRevokedApiKeyIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "revoked-key", "READ_ONLY", null, null, true);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredApiKeyIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "expired-key", "READ_ONLY", null, Instant.now().minus(1, ChronoUnit.DAYS), false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithApiKeyOutsideIpAllowlistIsRejected() throws Exception {
        String rawKey = insertApiKey(extensionId, "cidr-key", "READ_ONLY", "10.0.0.0/8", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithApiKeyInsideIpAllowlistSucceeds() throws Exception {
        String rawKey = insertApiKey(extensionId, "cidr-key-ok", "READ_ONLY", "127.0.0.1/32", null, false);

        mockMvc.perform(get("/api/v1/me").header("x-api-key", rawKey))
                .andExpect(status().isOk());
    }

    @Test
    void healthIsPubliclyAccessibleWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
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

        String token = JsonPath.read(loginResponse, "$.token");

        mockMvc.perform(get("/api/admin/extensions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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

    private String insertApiKey(UUID extId, String label, String scope, String ipAllowlist, Instant expiresAt, boolean revoked) throws Exception {
        String rawKey = "prefix_" + UUID.randomUUID();
        String prefix = rawKey.substring(0, 8);
        String hashedKey = hashingService.hash(rawKey);

        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO api_key (id, extension_id, label, key_prefix, hashed_key, scope, ip_allowlist, expires_at, revoked, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extId);
                ps.setString(3, label);
                ps.setString(4, prefix);
                ps.setString(5, hashedKey);
                ps.setString(6, scope);
                ps.setString(7, ipAllowlist);
                if (expiresAt != null) {
                    ps.setTimestamp(8, Timestamp.from(expiresAt));
                } else {
                    ps.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                ps.setBoolean(9, revoked);
                ps.setTimestamp(10, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }

        return rawKey;
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

