package pl.stapik.cloud.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.admin.data.AdminUserData;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserRepositoryIT extends AbstractIntegrationTest {
    @Autowired
    private AdminUserRepository adminUserRepository;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();
    }

    @Test
    void shouldFindUserByUsernameWhenExists() {
        // given
        String username = "testUser";

        AdminUserData user = new AdminUserData();
        user.setUsername(username);
        user.setPasswordHash("someHashedPassword");
        user.setRole("VIEWER");
        user.setCreatedAt(Instant.now());

        adminUserRepository.saveAndFlush(user);

        // when
        Optional<AdminUserData> result = adminUserRepository.findByUsername(username);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo(username);
        assertThat(result.get().getRole()).isEqualTo("VIEWER");
    }

    @Test
    void shouldReturnEmptyWhenUsernameDoesNotExist() {
        // given
        String username = "nonExistentUser";

        // when
        Optional<AdminUserData> result = adminUserRepository.findByUsername(username);

        // then
        assertThat(result).isEmpty();
    }
}
