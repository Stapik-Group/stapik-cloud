package pl.stapik.cloud.document;

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
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class AdminDocumentDelegateTest extends AbstractIntegrationTest {

    private static final String SLOT_KEY = "calendar.json";
    private static final String DOCUMENT_URL = "/api/admin/extensions/{extensionId}/documents/{slotKey}";
    private static final Instant DOCUMENT_UPDATED_AT = Instant.parse("2026-07-18T10:00:00Z");
    private static final String AUDIT_ACTION = "DOCUMENT_CONTENT_EDITED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private UUID extensionId;
    private UUID documentId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM audit_log_entry");
            connection.createStatement().execute("DELETE FROM document_version");
            connection.createStatement().execute("DELETE FROM document");
            connection.createStatement().execute("DELETE FROM document_slot");
            connection.createStatement().execute("DELETE FROM extension");
        }

        extensionId = UUID.randomUUID();
        insertExtension(extensionId);
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, SLOT_KEY);
        documentId = insertDocument(slotId, "{\"foo\": \"bar\"}");
    }

    @Test
    void shouldUpdateDocumentContentSuccessfully() throws Exception {
        // given
        String requestBody = writeRequestBody("{\\\"foo\\\": \\\"edited\\\"}", DOCUMENT_UPDATED_AT.toString());

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotKey").value(SLOT_KEY))
                .andExpect(jsonPath("$.content").value("{\"foo\": \"edited\"}"));

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"edited\"}");
        assertThat(countVersions(documentId)).isEqualTo(1);
        assertThat(countAuditEntries(AUDIT_ACTION)).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDocumentChangedAfterEditorLoadedIt() throws Exception {
        // given
        String staleLastKnownUpdate = DOCUMENT_UPDATED_AT.minusSeconds(60).toString();
        String requestBody = writeRequestBody("edited", staleLastKnownUpdate);

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"bar\"}");
        assertThat(countVersions(documentId)).isZero();
        assertThat(countAuditEntries(AUDIT_ACTION)).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenSlotDoesNotExistOnUpdate() throws Exception {
        // given
        String requestBody = writeRequestBody("edited", DOCUMENT_UPDATED_AT.toString());

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, "missing-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenClientLastKnownUpdateIsMissing() throws Exception {
        // given
        String requestBody = "{\"content\": \"edited\"}";

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"bar\"}");
    }

    private String writeRequestBody(String escapedContent, String clientLastKnownUpdate) {
        return "{\"content\": \"" + escapedContent + "\", \"clientLastKnownUpdate\": \"" + clientLastKnownUpdate + "\"}";
    }

    private String readContent(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT content FROM document WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private int countVersions(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM document_version WHERE document_id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int countAuditEntries(String action) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM audit_log_entry WHERE action = ?")) {
            statement.setString(1, action);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private void insertExtension(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, "admin-doc-ext");
                ps.setString(3, "Admin Document Extension");
                ps.setBoolean(4, true);
                ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertDocumentSlot(UUID id, UUID extensionId, String slotKey) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "JSON");
                ps.setLong(5, 1_048_576L);
                ps.setBoolean(6, true);
                ps.setInt(7, 10);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setTimestamp(9, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private UUID insertDocument(UUID slotId, String content) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document (id, document_slot_id, content, content_hash, updated_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, slotId);
                ps.setString(3, content);
                ps.setString(4, "hash-initial");
                ps.setTimestamp(5, java.sql.Timestamp.from(DOCUMENT_UPDATED_AT));
                ps.executeUpdate();
            }
        }
        return id;
    }
}
