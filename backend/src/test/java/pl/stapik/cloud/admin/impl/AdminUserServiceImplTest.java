package pl.stapik.cloud.admin.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.dto.Credentials;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Test
    void shouldReturnUserWhenCredentialsAreValid() {
        // given
        String username = "adminUser";
        String rawPassword = "secretPassword";
        String encodedPassword = "encodedSecretPassword";

        AdminUserData mockUser = mock(AdminUserData.class);
        given(mockUser.getPasswordHash()).willReturn(encodedPassword);

        given(adminUserRepository.findByUsername(username)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);

        // when
        Optional<AdminUserData> result = adminUserService.authenticate(Credentials.of(username, rawPassword));

        // then
        assertThat(result)
                .isPresent()
                .contains(mockUser);
    }

    @Test
    void shouldReturnEmptyWhenPasswordIsInvalid() {
        // given
        String username = "adminUser";
        String rawPassword = "wrongPassword";
        String encodedPassword = "encodedSecretPassword";

        AdminUserData mockUser = mock(AdminUserData.class);
        given(mockUser.getPasswordHash()).willReturn(encodedPassword);

        given(adminUserRepository.findByUsername(username)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(false);

        // when
        Optional<AdminUserData> result = adminUserService.authenticate(Credentials.of(username, rawPassword));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        // given
        String username = "unknownUser";
        String rawPassword = "anyPassword";

        given(adminUserRepository.findByUsername(username)).willReturn(Optional.empty());

        // when
        Optional<AdminUserData> result = adminUserService.authenticate(Credentials.of(username, rawPassword));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldReturnEmptyWhenUsernameNull() {
        // given
        String rawPassword = "anyPassword";

        given(adminUserRepository.findByUsername(null)).willReturn(Optional.empty());

        // when
        Optional<AdminUserData> result = adminUserService.authenticate(Credentials.of(null, rawPassword));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(passwordEncoder);
    }
}