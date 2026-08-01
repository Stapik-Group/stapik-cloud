package pl.stapik.cloud.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@AutoConfigureMockMvc
class DocumentDataDelegateTest extends AbstractIntegrationTest {
    private static final String API_KEY_HEADER = "x-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private UUID extensionId;
    private String rawApiKey;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM document_version");
            connection.createStatement().execute("DELETE FROM document");
            connection.createStatement().execute("DELETE FROM document_slot");
            connection.createStatement().execute("DELETE FROM api_key");
            connection.createStatement().execute("DELETE FROM extension");
        }

        extensionId = UUID.randomUUID();
        insertExtension(extensionId);

        rawApiKey = "test-api-key-" + UUID.randomUUID();
        insertApiKey(extensionId, rawApiKey);
    }

    @Test
    void shouldGetDocumentSuccessfully() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "notes");
        insertDocument(slotId, "hello world", "hash1");

        String expectedResponseBody = readResource("fixtures/documents/get-document-response.json");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}", "notes")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.slotKey").value("notes"))
                .andExpect(jsonPath("$.content").value("hello world"));
    }

    @Test
    void shouldReturnNotFoundWhenSlotDoesNotExistOnGet() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}", "missing-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnauthorizedWithoutApiKey() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}", "notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldWriteDocumentSuccessfully() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "write-slot");

        String requestBody = readResource("fixtures/documents/write-document-request.json");
        String expectedResponseBody = readResource("fixtures/documents/write-document-response.json");

        // when & then
        mockMvc.perform(put("/api/v1/documents/{slotKey}", "write-slot")
                        .header(API_KEY_HEADER, rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.contentHash").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenSlotDoesNotExistOnWrite() throws Exception {
        // given
        String requestBody = readResource("fixtures/documents/write-document-request.json");

        // when & then
        mockMvc.perform(put("/api/v1/documents/{slotKey}", "missing-slot")
                        .header(API_KEY_HEADER, rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictOnLastWriteWins() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "conflict-slot");
        insertDocument(slotId, "server content", "hash-server");

        String requestBody = readResource("fixtures/documents/write-document-conflict-request.json");

        // when & then
        mockMvc.perform(put("/api/v1/documents/{slotKey}", "conflict-slot")
                        .header(API_KEY_HEADER, rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.content").value("server content"));
    }

    @Test
    void shouldDeleteDocumentSuccessfully() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "delete-slot");
        insertDocument(slotId, "to be deleted", "hash-del");

        // when & then
        mockMvc.perform(delete("/api/v1/documents/{slotKey}", "delete-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingDocument() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "empty-slot");

        // when & then
        mockMvc.perform(delete("/api/v1/documents/{slotKey}", "empty-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListVersionsSuccessfully() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "versions-slot");
        UUID documentId = insertDocument(slotId, "current", "hash-current");
        insertDocumentVersion(documentId, "current");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/versions", "versions-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].reason").value("NORMAL_WRITE"));
    }

    @Test
    void shouldReturnNotFoundWhenListingVersionsForMissingSlot() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/versions", "missing-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRestoreVersionSuccessfully() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "restore-slot");
        UUID documentId = insertDocument(slotId, "current content", "hash-current");
        UUID versionId = insertDocumentVersion(documentId, "old content");

        // when & then
        mockMvc.perform(post("/api/v1/documents/{slotKey}/versions/{versionId}/restore", "restore-slot", versionId)
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("old content"));
    }

    @Test
    void shouldReturnNotFoundWhenRestoringMissingVersion() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "restore-missing-slot");
        insertDocument(slotId, "current content", "hash-current");

        // when & then
        mockMvc.perform(post("/api/v1/documents/{slotKey}/versions/{versionId}/restore",
                        "restore-missing-slot", UUID.randomUUID())
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    private void insertExtension(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, icon_glyph, color, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, "doc-test-ext");
                ps.setString(3, "Doc Test Extension");
                ps.setString(4, "icon-default");
                ps.setString(5, "#000000");
                ps.setBoolean(6, true);
                ps.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertApiKey(UUID extensionId, String rawKey) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO api_key (id, extension_id, key_prefix, hashed_key, scope, revoked, label, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extensionId);
                ps.setString(3, rawKey.substring(0, 8));
                ps.setString(4, passwordEncoder.encode(rawKey));
                ps.setString(5, "READ_WRITE");
                ps.setBoolean(6, false);
                ps.setString(7, "test-key");
                ps.setTimestamp(8, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertDocumentSlot(UUID id, UUID extensionId, String slotKey) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, encryption_required, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "TEXT");
                ps.setLong(5, 1_048_576L);
                ps.setBoolean(6, true);
                ps.setInt(7, 10);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setBoolean(9, false);
                ps.setTimestamp(10, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private UUID insertDocument(UUID slotId, String content, String contentHash) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document (id, document_slot_id, content, content_hash, updated_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, slotId);
                ps.setString(3, content);
                ps.setString(4, contentHash);
                ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
        return id;
    }

    private UUID insertDocumentVersion(UUID documentId, String content) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_version (id, document_id, content, saved_at, reason) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, documentId);
                ps.setString(3, content);
                ps.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
                ps.setString(5, "NORMAL_WRITE");
                ps.executeUpdate();
            }
        }
        return id;
    }

}
