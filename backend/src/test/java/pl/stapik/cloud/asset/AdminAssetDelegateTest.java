package pl.stapik.cloud.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.stapik.cloud.AbstractIntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class AdminAssetDelegateTest extends AbstractIntegrationTest {

    private static final String SLOT_KEY = "files";
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private UUID extensionId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM asset");
            connection.createStatement().execute("DELETE FROM document_slot");
            connection.createStatement().execute("DELETE FROM extension");
        }

        extensionId = UUID.randomUUID();
        insertExtension(extensionId);
        insertSlot(extensionId, SLOT_KEY);
    }

    @Test
    void shouldUploadAndListAssetsSortedByFilename() throws Exception {
        // given
        upload("b.txt", "text/plain", "second".getBytes()).andExpect(status().isOk());
        upload("a.png", "image/png", PNG_BYTES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("a.png"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.previewable").value(true));

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}", extensionId, SLOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets.length()").value(2))
                .andExpect(jsonPath("$.assets[0].filename").value("a.png"))
                .andExpect(jsonPath("$.assets[1].filename").value("b.txt"))
                .andExpect(jsonPath("$.assets[1].previewable").value(false));
    }

    @Test
    void shouldServePreviewableAssetInlineWithItsContentType() throws Exception {
        // given
        upload("a.png", "image/png", PNG_BYTES);

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, "a.png")
                        .param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void shouldServeAssetAsAttachmentByDefault() throws Exception {
        // given
        upload("a.png", "image/png", PNG_BYTES);

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, "a.png"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    void shouldNeverServeNonPreviewableAssetInline() throws Exception {
        // given
        upload("page.html", "text/html", "<script>alert(1)</script>".getBytes());

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, "page.html")
                        .param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    void shouldDeleteAsset() throws Exception {
        // given
        upload("a.png", "image/png", PNG_BYTES);

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, "a.png"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}", extensionId, SLOT_KEY))
                .andExpect(jsonPath("$.assets").isEmpty());
    }

    @Test
    void shouldReturnNotFoundForMissingAsset() throws Exception {
        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, "missing.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMissingSlot() throws Exception {
        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/assets/{slotKey}", extensionId, "unknown"))
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(String filename, String contentType, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, bytes);
        return mockMvc.perform(multipart("/api/admin/extensions/{extensionId}/assets/{slotKey}/{filename}", extensionId, SLOT_KEY, filename)
                .file(file)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }));
    }

    private void insertExtension(UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO extension (id, slug, display_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, "admin-asset-ext");
                ps.setString(3, "Admin Asset Extension");
                ps.setBoolean(4, true);
                ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }

    private void insertSlot(UUID extensionId, String slotKey) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO document_slot (id, extension_id, slot_key, content_type, max_size_bytes, " +
                    "versioning_enabled, max_versions_retained, conflict_strategy, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, extensionId);
                ps.setString(3, slotKey);
                ps.setString(4, "BINARY_COLLECTION");
                ps.setLong(5, 1_048_576L);
                ps.setBoolean(6, false);
                ps.setInt(7, 1);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setTimestamp(9, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
    }
}
