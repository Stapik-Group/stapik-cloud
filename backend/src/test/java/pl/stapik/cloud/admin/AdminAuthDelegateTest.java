package pl.stapik.cloud.admin;

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
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AdminAuthDelegateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM admin_user");

            String insertSql = "INSERT INTO admin_user (id, username, password_hash, role) VALUES (?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {
                preparedStatement.setObject(1, UUID.randomUUID());
                preparedStatement.setString(2, "adminUser");
                preparedStatement.setString(3, passwordEncoder.encode("secretPassword"));
                preparedStatement.setString(4, "VIEWER");
                preparedStatement.executeUpdate();
            }
        }
    }

    @Test
    void shouldLoginSuccessfullyAndReturnToken() throws Exception {
        // given
        String payload = """
                {
                    "username": "adminUser",
                    "password": "secretPassword"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @ParameterizedTest(name = "[{index}] Request: {0} -> Expected Status: {2}")
    @MethodSource("provideFailedLoginTestCases")
    void shouldFailToLogin(String requestPath, String expectedResponsePath, int expectedStatus) throws Exception {
        // given
        String requestBody = readResource(requestPath);
        String expectedResponseBody = readResource(expectedResponsePath);

        // when & then
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().json(expectedResponseBody, JsonCompareMode.LENIENT));
    }

    private static Stream<Arguments> provideFailedLoginTestCases() {
        String basePath = "fixtures/admin/auth/";
        return Stream.of(
                Arguments.of(
                        basePath + "login-wrong-password-request.json",
                        basePath + "login-wrong-password-response.json",
                        HttpStatus.UNAUTHORIZED.value()
                ),
                Arguments.of(
                        basePath + "login-missing-username-request.json",
                        basePath + "login-missing-username-response.json",
                        HttpStatus.BAD_REQUEST.value()
                ),
                Arguments.of(
                        basePath + "login-missing-password-request.json",
                        basePath + "login-missing-password-response.json",
                        HttpStatus.BAD_REQUEST.value()
                )
        );
    }
}