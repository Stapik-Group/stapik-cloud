package pl.stapik.cloud.admin.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.PasswordVerificationException;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginAttemptTracker loginAttemptTracker;

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

    @Test
    void shouldChangePasswordAndSignOutOtherSessions() {
        // given
        UUID adminUserId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder()
                .id(adminUserId)
                .username("adminUser")
                .passwordHash("old-hash")
                .build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));
        given(passwordEncoder.matches("current-password", "old-hash")).willReturn(true);
        given(passwordEncoder.encode("new-password-123")).willReturn("new-hash");

        // when
        adminUserService.changePassword(adminUserId, currentSessionId, "current-password", "new-password-123");

        // then
        assertThat(adminUser.getPasswordHash()).isEqualTo("new-hash");
        verify(adminUserRepository).save(adminUser);
        verify(refreshTokenRepository).deleteByAdminUserIdAndSessionIdNot(adminUserId, currentSessionId);
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder()
                .id(adminUserId)
                .username("adminUser")
                .passwordHash("old-hash")
                .build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));
        given(passwordEncoder.matches("wrong", "old-hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> adminUserService.changePassword(adminUserId, UUID.randomUUID(), "wrong", "new-password-123"))
                .isInstanceOf(PasswordVerificationException.class);
        verify(loginAttemptTracker).recordFailure("adminUser");
        verify(adminUserRepository, never()).save(adminUser);
        verifyNoInteractions(refreshTokenRepository);
    }
}
