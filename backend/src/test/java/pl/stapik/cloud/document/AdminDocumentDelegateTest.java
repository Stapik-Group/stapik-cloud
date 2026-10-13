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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class AdminDocumentDelegateTest extends AbstractIntegrationTest {

    private static final String SLOT_KEY = "calendar.json";
    private static final String DOCUMENT_URL = "/api/admin/extensions/{extensionId}/documents/{slotKey}";
    private static final String DIFF_URL = DOCUMENT_URL + "/versions/diff";
    private static final Instant DOCUMENT_UPDATED_AT = Instant.parse("2026-07-18T10:00:00Z");
    private static final String DOCUMENT_ETAG = "\"hash-initial\"";
    private static final String AUDIT_ACTION = "DOCUMENT_CONTENT_EDITED";
    private static final String ENCRYPTED_CONTENT = "{\"format\":\"stapik-e2e\",\"version\":1}";

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
        insertDocumentSlot(slotId, extensionId);
        documentId = insertDocument(slotId);
    }

    @Test
    void shouldUpdateDocumentContentSuccessfully() throws Exception {
        // given
        String requestBody = writeRequestBody("{\\\"foo\\\": \\\"edited\\\"}");

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .header("If-Match", DOCUMENT_ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.slotKey").value(SLOT_KEY))
                .andExpect(jsonPath("$.content").value("{\"foo\": \"edited\"}"));

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"edited\"}");
        assertThat(countVersions(documentId)).isEqualTo(1);
        assertThat(countAuditEntries()).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDocumentChangedAfterEditorLoadedIt() throws Exception {
        // given
        String requestBody = writeRequestBody("edited");

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .header("If-Match", "\"hash-stale\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"bar\"}");
        assertThat(countVersions(documentId)).isZero();
        assertThat(countAuditEntries()).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenSlotDoesNotExistOnUpdate() throws Exception {
        // given
        String requestBody = writeRequestBody("edited");

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, "missing-slot")
                        .header("If-Match", DOCUMENT_ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenIfMatchIsMissing() throws Exception {
        // given
        String requestBody = writeRequestBody("edited");

        // when & then
        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());

        assertThat(readContent(documentId)).isEqualTo("{\"foo\": \"bar\"}");
    }

    @Test
    void shouldCompareTwoVersions() throws Exception {
        // given
        UUID fromId = insertVersion(documentId, "{\"foo\": \"bar\", \"gone\": 1}");
        UUID toId = insertVersion(documentId, "{\"foo\": \"baz\", \"new\": 2}");

        // when & then
        mockMvc.perform(get(DIFF_URL, extensionId, SLOT_KEY)
                        .param("from", fromId.toString())
                        .param("to", toId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from.versionId").value(fromId.toString()))
                .andExpect(jsonPath("$.to.versionId").value(toId.toString()))
                .andExpect(jsonPath("$.from.content").value("{\"foo\": \"bar\", \"gone\": 1}"))
                .andExpect(jsonPath("$.changes.length()").value(3))
                .andExpect(jsonPath("$.changes[0].path").value("/foo"))
                .andExpect(jsonPath("$.changes[0].type").value("CHANGED"))
                .andExpect(jsonPath("$.changes[1].path").value("/gone"))
                .andExpect(jsonPath("$.changes[1].type").value("REMOVED"))
                .andExpect(jsonPath("$.changes[2].path").value("/new"))
                .andExpect(jsonPath("$.changes[2].type").value("ADDED"));
    }

    @Test
    void shouldCompareVersionWithCurrentDocumentWhenToIsOmitted() throws Exception {
        // given
        UUID fromId = insertVersion(documentId, "{\"foo\": \"old\"}");

        // when & then
        mockMvc.perform(get(DIFF_URL, extensionId, SLOT_KEY)
                        .param("from", fromId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to.versionId").doesNotExist())
                .andExpect(jsonPath("$.to.content").value("{\"foo\": \"bar\"}"))
                .andExpect(jsonPath("$.changes[0].path").value("/foo"))
                .andExpect(jsonPath("$.changes[0].before").value("\"old\""))
                .andExpect(jsonPath("$.changes[0].after").value("\"bar\""));
    }

    @Test
    void shouldReturnNotFoundWhenComparedVersionDoesNotExist() throws Exception {
        mockMvc.perform(get(DIFF_URL, extensionId, SLOT_KEY)
                        .param("from", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldFlagDocumentAsNotEncrypted() throws Exception {
        mockMvc.perform(get(DOCUMENT_URL, extensionId, SLOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(false));
    }

    @Test
    void shouldFlagEncryptedDocumentAndRejectEditing() throws Exception {
        // given
        setContent(documentId, ENCRYPTED_CONTENT);

        // when & then
        mockMvc.perform(get(DOCUMENT_URL, extensionId, SLOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(true));

        mockMvc.perform(put(DOCUMENT_URL, extensionId, SLOT_KEY)
                        .header("If-Match", DOCUMENT_ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeRequestBody("edited")))
                .andExpect(status().isConflict());

        assertThat(readContent(documentId)).isEqualTo(ENCRYPTED_CONTENT);
        assertThat(countVersions(documentId)).isZero();
    }

    @Test
    void shouldRejectComparingEncryptedVersions() throws Exception {
        // given
        UUID fromId = insertVersion(documentId, ENCRYPTED_CONTENT);

        // when & then
        mockMvc.perform(get(DIFF_URL, extensionId, SLOT_KEY)
                        .param("from", fromId.toString()))
                .andExpect(status().isConflict());
    }

    private String writeRequestBody(String escapedContent) {
        return "{\"content\": \"" + escapedContent + "\"}";
    }

    private void setContent(UUID id, String content) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE document SET content = ? WHERE id = ?")) {
            statement.setString(1, content);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
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

    private int countAuditEntries() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM audit_log_entry WHERE action = ?")) {
            statement.setString(1, AdminDocumentDelegateTest.AUDIT_ACTION);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private UUID insertVersion(UUID documentId, String content) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_version (id, document_id, content, saved_at, reason) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, documentId);
                ps.setString(3, content);
                ps.setTimestamp(4, java.sql.Timestamp.from(DOCUMENT_UPDATED_AT));
                ps.setString(5, "NORMAL_WRITE");
                ps.executeUpdate();
            }
        }
        return id;
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

    private void insertDocumentSlot(UUID id, UUID extensionId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, extensionId);
                ps.setString(3, AdminDocumentDelegateTest.SLOT_KEY);
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

    private UUID insertDocument(UUID slotId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document (id, document_slot_id, content, content_hash, updated_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, slotId);
                ps.setString(3, "{\"foo\": \"bar\"}");
                ps.setString(4, "hash-initial");
                ps.setTimestamp(5, java.sql.Timestamp.from(DOCUMENT_UPDATED_AT));
                ps.executeUpdate();
            }
        }
        return id;
    }
}
