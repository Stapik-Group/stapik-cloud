package pl.stapik.cloud.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.stapik.cloud.AbstractIntegrationTest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AssetUploadDelegateTest extends AbstractIntegrationTest {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String OFFSET_HEADER = "Upload-Offset";
    private static final String CONTENT = "hello chunked world";

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
            connection.createStatement().execute("DELETE FROM asset_upload");
            connection.createStatement().execute("DELETE FROM asset");
            connection.createStatement().execute("DELETE FROM document_slot");
            connection.createStatement().execute("DELETE FROM api_key");
            connection.createStatement().execute("DELETE FROM extension");
        }

        extensionId = UUID.randomUUID();
        insertExtension(extensionId);

        rawApiKey = "test-api-key-" + UUID.randomUUID();
        insertApiKey(extensionId, rawApiKey);
        insertSlot(extensionId, "files", 1_048_576L);
    }

    @Test
    void shouldUploadFileInChunksAndResumeAfterInterruption() throws Exception {
        // given
        String uploadId = startUpload(CONTENT.length(), sha256(CONTENT));

        // when
        mockMvc.perform(appendChunk(uploadId, 0, CONTENT.substring(0, 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(6))
                .andExpect(jsonPath("$.asset").doesNotExist());

        mockMvc.perform(get("/api/v1/asset-uploads/{id}", uploadId).header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(6));

        mockMvc.perform(appendChunk(uploadId, 6, CONTENT.substring(6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(CONTENT.length()))
                .andExpect(jsonPath("$.asset.filename").value("big.bin"))
                .andExpect(jsonPath("$.asset.checksumSha256").value(sha256(CONTENT)));

        // then
        mockMvc.perform(get("/api/v1/assets/{slotKey}/{filename}", "files", "big.bin").header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(content().bytes(CONTENT.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get("/api/v1/asset-uploads/{id}", uploadId).header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenChunkOffsetDoesNotMatch() throws Exception {
        // given
        String uploadId = startUpload(CONTENT.length(), null);
        mockMvc.perform(appendChunk(uploadId, 0, CONTENT.substring(0, 6))).andExpect(status().isOk());

        // when & then
        mockMvc.perform(appendChunk(uploadId, 3, CONTENT.substring(3)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnUnprocessableContentAndDropUploadWhenChecksumDoesNotMatch() throws Exception {
        // given
        String uploadId = startUpload(CONTENT.length(), sha256("something else"));

        // when & then
        mockMvc.perform(appendChunk(uploadId, 0, CONTENT))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/v1/asset-uploads/{id}", uploadId).header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assets/{slotKey}", "files").header(API_KEY_HEADER, rawApiKey))
                .andExpect(jsonPath("$.assets.length()").value(0));
    }

    @Test
    void shouldRejectUploadLargerThanSlotLimit() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/asset-uploads")
                        .header(API_KEY_HEADER, rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotKey\": \"files\", \"filename\": \"big.bin\", \"sizeBytes\": 2097152}"))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void shouldAbortUpload() throws Exception {
        // given
        String uploadId = startUpload(CONTENT.length(), null);

        // when & then
        mockMvc.perform(delete("/api/v1/asset-uploads/{id}", uploadId).header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/asset-uploads/{id}", uploadId).header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder appendChunk(String uploadId, long offset, String data) {
        return patch("/api/v1/asset-uploads/{id}", uploadId)
                .header(API_KEY_HEADER, rawApiKey)
                .header(OFFSET_HEADER, offset)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(data.getBytes(StandardCharsets.UTF_8));
    }

    private String startUpload(long sizeBytes, String checksum) throws Exception {
        String checksumField = checksum == null ? "" : ", \"checksumSha256\": \"" + checksum + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/asset-uploads")
                        .header(API_KEY_HEADER, rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotKey\": \"files\", \"filename\": \"big.bin\", \"sizeBytes\": " + sizeBytes + checksumField + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offset").value(0))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"uploadId\":\"") + "\"uploadId\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private void insertExtension(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, "upload-ext-" + id.toString().substring(0, 8));
            ps.setString(3, "Upload Extension");
            ps.setBoolean(4, true);
            ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
            ps.executeUpdate();
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
                    "versioning_enabled, max_versions_retained, conflict_strategy, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "BINARY_COLLECTION");
                ps.setLong(5, maxSizeBytes);
                ps.setBoolean(6, false);
                ps.setInt(7, 1);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setTimestamp(9, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }
}