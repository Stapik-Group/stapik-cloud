package pl.stapik.cloud.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.admin.totp.TotpService;
import pl.stapik.cloud.common.crypto.HashingService;
import pl.stapik.cloud.security.admin.JwtService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminTwoFactorE2ETest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HashingService hashingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TotpService totpService;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DELETE FROM admin_user");
        }
    }

    @Test
    void shouldRequireSecondFactorOnlyAfterTwoFactorIsEnabled() throws Exception {
        insertAdmin("alice", "OWNER");
        String secret = enableTwoFactor("alice").secret();

        String loginBody = login("alice", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.challengeToken").isNotEmpty())
                .andExpect(jsonPath("$.session").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String challengeToken = JsonPath.read(loginBody, "$.challengeToken");

        completeLogin(challengeToken, nextStepCode(secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void shouldRejectReplayOfTheSameCode() throws Exception {
        insertAdmin("bob", "OWNER");
        String secret = enableTwoFactor("bob").secret();
        String code = nextStepCode(secret);

        completeLogin(challengeFor("bob"), code).andExpect(status().isOk());
        completeLogin(challengeFor("bob"), code).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptBackupCodeOnlyOnce() throws Exception {
        insertAdmin("carol", "OWNER");
        String backupCode = enableTwoFactor("carol").backupCodes().getFirst();

        completeLogin(challengeFor("carol"), backupCode).andExpect(status().isOk());
        completeLogin(challengeFor("carol"), backupCode).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongCodeAndGarbageChallenge() throws Exception {
        insertAdmin("dave", "OWNER");
        enableTwoFactor("dave");

        completeLogin(challengeFor("dave"), "000000").andExpect(status().isUnauthorized());
        completeLogin("not-a-challenge", "000000").andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAcceptChallengeAsAccessToken() throws Exception {
        insertAdmin("erin", "OWNER");
        enableTwoFactor("erin");

        mockMvc.perform(get("/api/admin/account").header("Authorization", "Bearer " + challengeFor("erin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAcceptAccessTokenAsChallenge() throws Exception {
        UUID adminUserId = insertAdmin("frank", "OWNER");
        enableTwoFactor("frank");

        completeLogin(jwtService.generateToken(adminUserId, "frank", "OWNER"), "000000")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSetupWhenAlreadyEnabled() throws Exception {
        insertAdmin("grace", "OWNER");
        enableTwoFactor("grace");

        mockMvc.perform(post("/api/admin/account/two-factor/setup").header("Authorization", bearer("grace", "OWNER")))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDisableOnlyWithPasswordAndCode() throws Exception {
        insertAdmin("heidi", "OWNER");
        List<String> backupCodes = enableTwoFactor("heidi").backupCodes();

        disable("heidi", "wrong-password", backupCodes.get(0)).andExpect(status().isBadRequest());
        disable("heidi", PASSWORD, backupCodes.get(0)).andExpect(status().isNoContent());

        login("heidi", PASSWORD)
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.session.token").isNotEmpty());
    }

    @Test
    void shouldReportAccountStateAndRegenerateBackupCodes() throws Exception {
        insertAdmin("ivan", "VIEWER");
        mockMvc.perform(get("/api/admin/account").header("Authorization", bearer("ivan", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));

        TwoFactorEnrollment enrollment = enableTwoFactor("ivan", "VIEWER");

        mockMvc.perform(post("/api/admin/account/two-factor/backup-codes")
                        .header("Authorization", bearer("ivan", "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation(PASSWORD, enrollment.backupCodes().getFirst())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes.length()").value(10));
        mockMvc.perform(get("/api/admin/account").header("Authorization", bearer("ivan", "VIEWER")))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.backupCodesRemaining").value(10));
    }

    @Test
    void shouldLetOwnerResetAnotherAdminsTwoFactor() throws Exception {
        insertAdmin("owner", "OWNER");
        UUID lockedOutId = insertAdmin("judy", "VIEWER");
        enableTwoFactor("judy", "VIEWER");

        mockMvc.perform(post("/api/admin/admin-users/{id}/two-factor/reset", lockedOutId)
                        .header("Authorization", bearer("owner", "OWNER")))
                .andExpect(status().isNoContent());

        login("judy", PASSWORD)
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.session.token").isNotEmpty());
    }

    @Test
    void shouldForbidNonOwnerFromResettingTwoFactor() throws Exception {
        UUID viewerId = insertAdmin("mallory", "VIEWER");

        mockMvc.perform(post("/api/admin/admin-users/{id}/two-factor/reset", viewerId)
                        .header("Authorization", bearer("mallory", "VIEWER")))
                .andExpect(status().isForbidden());
    }

    private record TwoFactorEnrollment(String secret, List<String> backupCodes) { }

    private TwoFactorEnrollment enableTwoFactor(String username) throws Exception {
        return enableTwoFactor(username, "OWNER");
    }

    private TwoFactorEnrollment enableTwoFactor(String username, String role) throws Exception {
        String setupBody = mockMvc.perform(post("/api/admin/account/two-factor/setup")
                        .header("Authorization", bearer(username, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCodeSvg").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setupBody, "$.secret");

        String enableBody = mockMvc.perform(post("/api/admin/account/two-factor/enable")
                        .header("Authorization", bearer(username, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(totpService.codeFor(secret, Instant.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        List<String> backupCodes = JsonPath.read(enableBody, "$.backupCodes");
        return new TwoFactorEnrollment(secret, backupCodes);
    }

    private String nextStepCode(String secret) {
        return totpService.codeFor(secret, Instant.now().plusSeconds(30));
    }

    private String challengeFor(String username) throws Exception {
        String body = login(username, PASSWORD).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.challengeToken");
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private ResultActions completeLogin(String challengeToken, String code) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login/two-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(challengeToken, code)));
    }

    private ResultActions disable(String username, String password, String code) throws Exception {
        return mockMvc.perform(post("/api/admin/account/two-factor/disable")
                .header("Authorization", bearer(username, "OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmation(password, code)));
    }

    private String confirmation(String password, String code) {
        return "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(password, code);
    }

    private String bearer(String username, String role) throws Exception {
        UUID adminUserId = findAdminUserId(username);
        return "Bearer " + jwtService.generateToken(adminUserId, username, role);
    }

    private UUID findAdminUserId(String username) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT id FROM admin_user WHERE username = ?")) {
            ps.setString(1, username);
            var rows = ps.executeQuery();
            assertThat(rows.next()).isTrue();
            return rows.getObject(1, UUID.class);
        }
    }

    private UUID insertAdmin(String username, String role) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO admin_user (id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, username);
            ps.setString(3, hashingService.hash(PASSWORD));
            ps.setString(4, role);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
        return id;
    }
}
