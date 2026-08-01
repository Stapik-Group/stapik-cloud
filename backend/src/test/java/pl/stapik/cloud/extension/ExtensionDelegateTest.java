package pl.stapik.cloud.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
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
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc(addFilters = false)
class ExtensionDelegateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM api_key");
            connection.createStatement().execute("DELETE FROM extension");
        }
    }

    @Test
    void shouldListExtensionsSuccessfully() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        insertExtension(extensionId, "list-slug", "List Ext");

        String expectedResponseBody = readResource("fixtures/admin/extensions/extensions-list.json");

        // when & then
        mockMvc.perform(get("/api/admin/extensions"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.extensions[0].id").value(extensionId.toString()))
                .andExpect(jsonPath("$.extensions[0].createdAt").isNotEmpty());
    }

    @ParameterizedTest(name = "[{index}] Request: {0} -> Expected Status: {2}")
    @MethodSource("provideTestCases")
    void shouldGiveExpectedResponseForCreatingExtension(String requestPath, String expectedResponsePath, int expectedStatus) throws Exception {
        // given
        String requestBody = readResource(requestPath);
        String expectedResponseBody = readResource(expectedResponsePath);

        // when & then
        mockMvc.perform(post("/api/admin/extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT));
    }

    @Test
    void shouldThrowDuplicatedSlugWhenTryingToAddSameSlugs() throws Exception {
        // given
        insertExtension(UUID.randomUUID(), "example-slug", "First extension");

        String requestBody = readResource("fixtures/admin/extensions/create-extension-duplicated-slug.json");
        String expectedResponseBody = readResource("fixtures/admin/extensions/create-extension-duplicated-slug-response.json");

        // when & then
        mockMvc.perform(post("/api/admin/extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT));
    }

    @Test
    void shouldGetExtensionSuccessfully() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        insertExtension(extensionId, "single-extension-slug", "Single extension");

        String expectedResponseBody = readResource("fixtures/admin/extensions/extensions-get.json");

        // when & then
        mockMvc.perform(get("/api/admin/extensions/" + extensionId))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.id").value(extensionId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void shouldReturnNotExist() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/admin/extensions/" + extensionId))
                .andExpect(status().isNotFound());
    }

    private static Stream<Arguments> provideTestCases() {
        String basePath = "fixtures/admin/extensions/";
        return Stream.of(
                Arguments.of(
                        basePath + "create-extension-success.json",
                        basePath + "create-extension-success-response.json",
                        HttpStatus.CREATED.value()
                ),
                Arguments.of(
                        basePath + "create-extension-no-slug.json",
                        basePath + "create-extension-no-slug-response.json",
                        HttpStatus.BAD_REQUEST.value()
                )
        );
    }

    @Test
    void shouldDeleteExtensionSuccessfully() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();
        insertExtension(extensionId, "delete-slug", "Delete Ext");

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}", extensionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGotNotFoundWhenDeleteExtension() throws Exception {
        // given
        UUID extensionId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/admin/extensions/{extensionId}", extensionId))
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
}
