package pl.stapik.cloud.documentslot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

@AutoConfigureMockMvc(addFilters = false)
class DocumentDataSlotDelegateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private UUID extensionId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM document_slot");
            connection.createStatement().execute("DELETE FROM extension");
        }

        extensionId = UUID.randomUUID();
        insertExtension(extensionId, "slot-test-ext", "Slot Test Extension");
    }

    @Test
    void shouldListSlotsSuccessfully() throws Exception {
        // given
        insertSlot(extensionId, "notes");

        String expectedResponseBody = readResource("fixtures/slots/slots-list.json");

        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/slots", extensionId))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.slots[0].slotKey").value("notes"));
    }

    @Test
    void shouldReturnEmptyListWhenExtensionHasNoSlots() throws Exception {
        // when & then
        mockMvc.perform(get("/api/admin/extensions/{extensionId}/slots", extensionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isEmpty());
    }

    @Test
    void shouldCreateSlotWithAllFieldsProvided() throws Exception {
        // given
        String requestBody = readResource("fixtures/slots/create-slot-all-fields-request.json");
        String expectedResponseBody = readResource("fixtures/slots/create-slot-all-fields-response.json");

        // when & then
        mockMvc.perform(post("/api/admin/extensions/{extensionId}/slots", extensionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void shouldCreateSlotUsingDefaultsWhenOptionalFieldsOmitted() throws Exception {
        // given
        String requestBody = readResource("fixtures/slots/create-slot-minimal-request.json");

        // when & then
        mockMvc.perform(post("/api/admin/extensions/{extensionId}/slots", extensionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotKey").value("minimal-slot"))
                .andExpect(jsonPath("$.contentType").value("TEXT"))
                .andExpect(jsonPath("$.maxSizeBytes").value(1_048_576))
                .andExpect(jsonPath("$.versioningEnabled").value(true))
                .andExpect(jsonPath("$.maxVersionsRetained").value(20))
                .andExpect(jsonPath("$.conflictStrategy").value("LAST_WRITE_WINS_WITH_SHADOW_COPY"))
                .andExpect(jsonPath("$.encryptionRequired").value(false));
    }

    @Test
    void shouldReturnNotFoundWhenCreatingSlotForMissingExtension() throws Exception {
        // given
        UUID missingExtensionId = UUID.randomUUID();
        String requestBody = readResource("fixtures/slots/create-slot-minimal-request.json");

        // when & then
        mockMvc.perform(post("/api/admin/extensions/{extensionId}/slots", missingExtensionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteSlotSuccessfully() throws Exception {
        // given
        UUID slotId = insertSlot(extensionId, "delete-me");

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/slots/{slotId}", extensionId, slotId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingSlot() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/slots/{slotId}", extensionId, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingSlotBelongingToDifferentExtension() throws Exception {
        // given
        UUID otherExtensionId = UUID.randomUUID();
        insertExtension(otherExtensionId, "other-ext", "Other Extension");
        UUID slotId = insertSlot(otherExtensionId, "foreign-slot");

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}/slots/{slotId}", extensionId, slotId))
                .andExpect(status().isNotFound());
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

    private UUID insertSlot(UUID extensionId, String slotKey) throws Exception {
        UUID id = UUID.randomUUID();
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
                ps.setInt(7, 20);
                ps.setString(8, "LAST_WRITE_WINS");
                ps.setBoolean(9, false);
                ps.setTimestamp(10, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }
        return id;
    }
}