package pl.stapik.cloud.health;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.admin.data.ApiKeyScope;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
class SystemDelegateTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private UUID extensionId;

    @BeforeEach
    void setUp() throws Exception {
        extensionId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM api_key");
            connection.createStatement().execute("DELETE FROM extension");

            String sql = "INSERT INTO extension (id, slug, display_name, icon_glyph, color, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, extensionId);
                ps.setString(2, "system-slug");
                ps.setString(3, "System Ext");
                ps.setString(4, "icon-sys");
                ps.setString(5, "#000000");
                ps.setBoolean(6, true);
                ps.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnHealthStatusUp() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldReturnMeInformationForAuthenticatedUser() throws Exception {
        // given
        UUID apiKeyId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            String insertKey = "INSERT INTO api_key (id, extension_id, label, key_prefix, hashed_key, scope, revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insertKey)) {
                ps.setObject(1, apiKeyId);
                ps.setObject(2, extensionId);
                ps.setString(3, "My System Key");
                ps.setString(4, "prefix");
                ps.setString(5, "encoded-hash");
                ps.setString(6, "READ_WRITE");
                ps.setBoolean(7, false);
                ps.setTimestamp(8, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }

        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKeyId, extensionId, "My System Key", ApiKeyScope.WRITE);

        Authentication auth = new TestingAuthenticationToken(principal, null, "SCOPE_READ_WRITE");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // when & then
        mockMvc.perform(get("/api/v1/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensionId").value(extensionId.toString()))
                .andExpect(jsonPath("$.extensionSlug").value("system-slug"))
                .andExpect(jsonPath("$.keyLabel").value("My System Key"))
                .andExpect(jsonPath("$.scope").value("READ_WRITE"));
    }

    @Test
    void shouldReturnNotFoundErrorWhenExtensionIsNotFound() throws Exception {
        // given
        UUID nonExistentId = UUID.randomUUID();
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                UUID.randomUUID(), nonExistentId, "orphaned-key", ApiKeyScope.ONLY);

        Authentication auth = new TestingAuthenticationToken(principal, null, "SCOPE_READ_ONLY");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // when & then
        mockMvc.perform(get("/api/v1/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
