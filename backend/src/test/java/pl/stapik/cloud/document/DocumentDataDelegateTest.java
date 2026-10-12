package pl.stapik.cloud.document;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
                .andExpect(header().string("ETag", "\"hash1\""))
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
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldOverwriteExistingDocumentWhenIfMatchEqualsCurrentETag() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "etag-slot");
        insertDocument(slotId, "server content", "hash-server");

        // when & then
        mockMvc.perform(put("/api/v1/documents/{slotKey}", "etag-slot")
                        .header(API_KEY_HEADER, rawApiKey)
                        .header("If-Match", "\"hash-server\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"client content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("client content"))
                .andExpect(header().exists("ETag"));
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
                        .header("If-Match", "\"hash-stale\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(header().string("ETag", "\"hash-server\""))
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
    void shouldKeepOnlyConfiguredNumberOfVersionsAfterRepeatedWrites() throws Exception {
        // given
        int maxVersionsRetained = 3;
        int writesCount = 6;
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "limited-slot", maxVersionsRetained);

        // when
        for (int writeNumber = 1; writeNumber <= writesCount; writeNumber++) {
            putDocument("/api/v1/documents/{slotKey}", "content-" + writeNumber, "limited-slot");
        }

        // then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/versions", "limited-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(maxVersionsRetained));
    }

    @Test
    void shouldReportDeviceThatWroteVersionInHistory() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "device-slot");
        putDocument("/api/v1/documents/{slotKey}", "from device", "device-slot");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/versions", "device-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].deviceId").isNotEmpty())
                .andExpect(jsonPath("$.versions[0].deviceLabel").value("test-key"));
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

    @Test
    void shouldWriteAndReadPartitionIndependentlyFromMainDocument() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");

        // when
        putDocument("/api/v1/documents/{slotKey}", "main content", "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "content of 2024", "calendar.json", "2024");

        // then
        mockMvc.perform(get("/api/v1/documents/{slotKey}", "calendar.json").header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("main content"));
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotKey").value("calendar.json"))
                .andExpect(jsonPath("$.content").value("content of 2024"));
    }

    @Test
    void shouldListPartitionsWithoutMainDocument() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");
        putDocument("/api/v1/documents/{slotKey}", "main content", "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "year 2024", "calendar.json", "2024");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "zażółć 2023", "calendar.json", "2023");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions", "calendar.json")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions.length()").value(2))
                .andExpect(jsonPath("$.partitions[0].partition").value("2023"))
                .andExpect(jsonPath("$.partitions[0].sizeBytes").value("zażółć 2023".getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                .andExpect(jsonPath("$.partitions[0].contentHash").isNotEmpty())
                .andExpect(jsonPath("$.partitions[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.partitions[1].partition").value("2024"));
    }

    @Test
    void shouldReturnEmptyPartitionListForSlotWithoutPartitions() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions", "calendar.json")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions.length()").value(0));
    }

    @Test
    void shouldReturnNotFoundWhenListingPartitionsForMissingSlot() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions", "missing-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMissingPartition() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "2020")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestForInvalidPartitionKey() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");

        // when & then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "-invalid")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepVersionHistoryPerPartition() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");

        // when
        putDocument("/api/v1/documents/{slotKey}", "main content", "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "first", "calendar.json", "2024");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "second", "calendar.json", "2024");

        // then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/versions", "calendar.json").header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(1));
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}/versions", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2));
    }

    @Test
    void shouldRestorePartitionVersion() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "first", "calendar.json", "2024");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "second", "calendar.json", "2024");

        MvcResult versionsResult = mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}/versions", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andReturn();
        String firstVersionId = JsonPath.read(versionsResult.getResponse().getContentAsString(), "$.versions[1].id");

        // when & then
        mockMvc.perform(post("/api/v1/documents/{slotKey}/partitions/{partition}/versions/{versionId}/restore",
                        "calendar.json", "2024", firstVersionId)
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("first"));
    }

    @Test
    void shouldDeletePartitionWithoutTouchingMainDocument() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");
        putDocument("/api/v1/documents/{slotKey}", "main content", "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "year 2024", "calendar.json", "2024");

        // when
        mockMvc.perform(delete("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents/{slotKey}/partitions", "calendar.json").header(API_KEY_HEADER, rawApiKey))
                .andExpect(jsonPath("$.partitions.length()").value(0));
        mockMvc.perform(get("/api/v1/documents/{slotKey}", "calendar.json").header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("main content"));
    }

    @Test
    void shouldListChangesSinceGivenMoment() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "notes");
        insertDocument(slotId, "hello world", "hash1");

        // when & then
        mockMvc.perform(get("/api/v1/changes").param("since", Instant.now().minusSeconds(3600).toString())
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].slotKey").value("notes"))
                .andExpect(jsonPath("$.changes[0].partition").doesNotExist())
                .andExpect(jsonPath("$.changes[0].contentHash").value("hash1"))
                .andExpect(jsonPath("$.changes[0].deleted").value(false));
    }

    @Test
    void shouldListPartitionAndDeletedDocumentInChanges() throws Exception {
        // given
        insertDocumentSlot(UUID.randomUUID(), extensionId, "calendar.json");
        putDocument("/api/v1/documents/{slotKey}/partitions/{partition}", "year 2024", "calendar.json", "2024");
        mockMvc.perform(delete("/api/v1/documents/{slotKey}/partitions/{partition}", "calendar.json", "2024")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNoContent());

        // when & then
        mockMvc.perform(get("/api/v1/changes").param("since", Instant.now().minusSeconds(3600).toString())
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].slotKey").value("calendar.json"))
                .andExpect(jsonPath("$.changes[0].partition").value("2024"))
                .andExpect(jsonPath("$.changes[0].deleted").value(true));
    }

    @Test
    void shouldReturnNoChangesWhenNothingChangedSinceGivenMoment() throws Exception {
        // given
        UUID slotId = UUID.randomUUID();
        insertDocumentSlot(slotId, extensionId, "notes");
        insertDocument(slotId, "hello world", "hash1");

        // when & then
        mockMvc.perform(get("/api/v1/changes").param("since", Instant.now().plusSeconds(3600).toString())
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(0));
    }

    @Test
    void shouldNotListChangesOfOtherExtension() throws Exception {
        // given
        UUID otherExtensionId = UUID.randomUUID();
        insertExtension(otherExtensionId, "other-doc-test-ext");
        UUID otherSlotId = UUID.randomUUID();
        insertDocumentSlot(otherSlotId, otherExtensionId, "foreign");
        insertDocument(otherSlotId, "foreign content", "hash-foreign");

        // when & then
        mockMvc.perform(get("/api/v1/changes").param("since", Instant.now().minusSeconds(3600).toString())
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(0));
    }

    @Test
    void shouldReturnBadRequestWhenSinceIsMissing() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/changes").header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isBadRequest());
    }

    private void putDocument(String urlTemplate, String content, Object... urlVariables) throws Exception {
        String currentETag = mockMvc.perform(get(urlTemplate, urlVariables).header(API_KEY_HEADER, rawApiKey))
                .andReturn().getResponse().getHeader("ETag");
        MockHttpServletRequestBuilder request = put(urlTemplate, urlVariables)
                .header(API_KEY_HEADER, rawApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + content + "\"}");
        if (currentETag != null) {
            request.header("If-Match", currentETag);
        }
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private void insertExtension(UUID id) throws Exception {
        insertExtension(id, "doc-test-ext");
    }

    private void insertExtension(UUID id, String slug) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, slug);
                ps.setString(3, "Doc Test Extension");
                ps.setBoolean(4, true);
                ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
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
        insertDocumentSlot(id, extensionId, slotKey, 10);
    }

    private void insertDocumentSlot(UUID id, UUID extensionId, String slotKey, int maxVersionsRetained) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "TEXT");
                ps.setLong(5, 1_048_576L);
                ps.setBoolean(6, true);
                ps.setInt(7, maxVersionsRetained);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setTimestamp(9, java.sql.Timestamp.from(Instant.now()));
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
