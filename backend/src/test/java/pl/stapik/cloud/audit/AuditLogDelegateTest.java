package pl.stapik.cloud.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class AuditLogDelegateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM audit_log_entry");
            connection.createStatement().execute("DELETE FROM extension");
        }
    }

    @Test
    void shouldListAllEntriesWithDefaultPaging() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        insertExtension(extensionId, "audit-ext", "Audit Extension");

        Instant now = Instant.now();
        insertEntry(extensionId, "EXTENSION_CREATED", now.minusSeconds(60));
        insertEntry(extensionId, "EXTENSION_DELETED", now.minusSeconds(10));

        // when & then
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("EXTENSION_DELETED"))
                .andExpect(jsonPath("$.items[1].action").value("EXTENSION_CREATED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldFilterByExtensionId() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        UUID otherExtensionId = UUID.randomUUID();
        insertExtension(extensionId, "audit-ext-1", "Audit Extension 1");
        insertExtension(otherExtensionId, "audit-ext-2", "Audit Extension 2");

        insertEntry(extensionId, "EXTENSION_CREATED", Instant.now());
        insertEntry(otherExtensionId, "EXTENSION_CREATED", Instant.now());

        // when & then
        mockMvc.perform(get("/api/admin/audit-log").param("extensionId", extensionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].extensionId").value(extensionId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldRespectCustomPageAndSize() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        insertExtension(extensionId, "audit-paged-ext", "Audit Paged Extension");

        Instant now = Instant.now();
        insertEntry(extensionId, "ACTION_1", now.minusSeconds(30));
        insertEntry(extensionId, "ACTION_2", now.minusSeconds(20));
        insertEntry(extensionId, "ACTION_3", now.minusSeconds(10));

        // when & then
        mockMvc.perform(get("/api/admin/audit-log")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldReturnEmptyItemsWhenNoEntriesExist() throws Exception {
        // when & then
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void insertExtension(UUID id, String slug, String displayName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, icon_glyph, color, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, slug);
                ps.setString(3, displayName);
                ps.setString(4, "icon-default");
                ps.setString(5, "#000000");
                ps.setBoolean(6, true);
                ps.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertEntry(UUID extensionId, String action, Instant occurredAt) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO audit_log_entry (id, extension_id, actor, action, details, occurred_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extensionId);
                ps.setString(3, "test-actor");
                ps.setString(4, action);
                ps.setString(5, null);
                ps.setTimestamp(6, java.sql.Timestamp.from(occurredAt));
                ps.executeUpdate();
            }
        }
    }
}