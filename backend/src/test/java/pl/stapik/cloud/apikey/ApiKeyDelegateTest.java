package pl.stapik.cloud.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class ApiKeyDelegateTest extends AbstractIntegrationTest {

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

            String insertExt = "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insertExt)) {
                ps.setObject(1, extensionId);
                ps.setString(2, "test-slug");
                ps.setString(3, "Test Ext");
                ps.setBoolean(4, true);
                ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    @Test
    void shouldCreateApiKeySuccessfully() throws Exception {
        // given
        String payload = """
                {
                    "label": "My Test Key",
                    "scope": "READ_ONLY"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/admin/extensions/{extensionId}/keys", extensionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey").isNotEmpty())
                .andExpect(jsonPath("$.label").value("My Test Key"));
    }

    @Test
    void shouldRevokeApiKeySuccessfully() throws Exception {
        // given
        UUID keyId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String insertKey = "INSERT INTO api_key (id, extension_id, label, key_prefix, hashed_key, scope, revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insertKey)) {
                ps.setObject(1, keyId);
                ps.setObject(2, extensionId);
                ps.setString(3, "Revoke Me");
                ps.setString(4, "prefix");
                ps.setString(5, "hash");
                ps.setString(6, "READ_ONLY");
                ps.setBoolean(7, false);
                ps.setTimestamp(8, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/keys/{keyId}", extensionId, keyId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldListApiKeysSuccessfully() throws Exception {
        // given
        UUID keyId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String insertKey = "INSERT INTO api_key (id, extension_id, label, key_prefix, hashed_key, scope, revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insertKey)) {
                ps.setObject(1, keyId);
                ps.setObject(2, extensionId);
                ps.setString(3, "Listable Key");
                ps.setString(4, "pre");
                ps.setString(5, "hash");
                ps.setString(6, "READ_ONLY");
                ps.setBoolean(7, false);
                ps.setTimestamp(8, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/keys", extensionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].id").value(keyId.toString()));
    }

    @Test
    void shouldReturnBadRequestWhenCreatingWithInvalidData() throws Exception {
        // given
        String payload = """
                {
                    "label": "",
                    "scope": "INVALID_SCOPE"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/admin/extensions/{extensionId}/keys", extensionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenRevokingNonExistentKey() throws Exception {
        // given
        UUID nonExistentKeyId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/keys/{keyId}", extensionId, nonExistentKeyId))
                .andExpect(status().isNotFound());
    }
}