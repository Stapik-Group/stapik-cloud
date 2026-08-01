package pl.stapik.cloud.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class AssetDataDelegateTest extends AbstractIntegrationTest {

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
            connection.createStatement().execute("DELETE FROM asset");
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
    void shouldUploadAssetSuccessfully() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "hello world".getBytes());

        // when & then
        mockMvc.perform(multipart("/api/v1/assets/{slotKey}/{filename}", "files", "report.pdf")
                        .file(file)
                        .header(API_KEY_HEADER, rawApiKey)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("report.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value("hello world".getBytes().length))
                .andExpect(jsonPath("$.checksumSha256").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenUploadingToMissingSlot() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "hello world".getBytes());

        // when & then
        mockMvc.perform(multipart("/api/v1/assets/{slotKey}/{filename}", "missing-slot", "report.pdf")
                        .file(file)
                        .header(API_KEY_HEADER, rawApiKey)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnPayloadTooLargeWhenFileExceedsSlotLimit() throws Exception {
        // given
        insertSlot(extensionId, "small-slot", 5L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "hello world".getBytes());

        // when & then
        mockMvc.perform(multipart("/api/v1/assets/{slotKey}/{filename}", "small-slot", "report.pdf")
                        .file(file)
                        .header(API_KEY_HEADER, rawApiKey)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void shouldListAssetsSuccessfully() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);
        uploadFile("one.txt", "content one");
        uploadFile("two.txt", "content two");

        // when & then
        mockMvc.perform(get("/api/v1/assets/{slotKey}", "files")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets.length()").value(2));
    }

    @Test
    void shouldReturnNotFoundWhenListingAssetsForMissingSlot() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/assets/{slotKey}", "missing-slot")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDownloadAssetSuccessfully() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);
        uploadFile("download-me.txt", "downloadable content");

        // when & then
        mockMvc.perform(get("/api/v1/assets/{slotKey}/{filename}", "files", "download-me.txt")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(content().bytes("downloadable content".getBytes()));
    }

    @Test
    void shouldReturnNotFoundWhenDownloadingMissingAsset() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);

        // when & then
        mockMvc.perform(get("/api/v1/assets/{slotKey}/{filename}", "files", "missing.txt")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteAssetSuccessfully() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);
        uploadFile("delete-me.txt", "to be deleted");

        // when & then
        mockMvc.perform(delete("/api/v1/assets/{slotKey}/{filename}", "files", "delete-me.txt")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/assets/{slotKey}/{filename}", "files", "delete-me.txt")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingAsset() throws Exception {
        // given
        insertSlot(extensionId, "files", 1_048_576L);

        // when & then
        mockMvc.perform(delete("/api/v1/assets/{slotKey}/{filename}", "files", "missing.txt")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    private void uploadFile(String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content.getBytes());
        mockMvc.perform(multipart("/api/v1/assets/{slotKey}/{filename}", "files", filename)
                        .file(file)
                        .header(API_KEY_HEADER, rawApiKey)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());
    }

    private void insertExtension(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, icon_glyph, color, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, "asset-test-ext");
                ps.setString(3, "Asset Test Extension");
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

    private void insertSlot(UUID extensionId, String slotKey, long maxSizeBytes) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, encryption_required, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "BINARY_COLLECTION");
                ps.setLong(5, maxSizeBytes);
                ps.setBoolean(6, false);
                ps.setInt(7, 1);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setBoolean(9, false);
                ps.setTimestamp(10, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }
}