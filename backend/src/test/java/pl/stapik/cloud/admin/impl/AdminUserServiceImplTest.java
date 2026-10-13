package pl.stapik.cloud.admin.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.AdminUserStateException;
import pl.stapik.cloud.admin.PasswordGenerator;
import pl.stapik.cloud.admin.PasswordVerificationException;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.dto.AdminUserChanges;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.admin.dto.NewAdminUser;
import pl.stapik.cloud.admin.dto.PasswordReset;
import pl.stapik.cloud.common.error.DuplicateResourceException;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private PasswordGenerator passwordGenerator;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-07-26T22:45:00Z"), ZoneOffset.UTC);

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

    @Test
    void shouldCreateAdminUserWithEncodedPassword() {
        // given
        given(adminUserRepository.existsByUsernameIgnoreCase("newAdmin")).willReturn(false);
        given(passwordEncoder.encode("long-enough-password")).willReturn("encoded");
        given(adminUserRepository.save(any(AdminUserData.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AdminUserData created = adminUserService.create(new NewAdminUser("newAdmin", "long-enough-password", "VIEWER"));

        // then
        assertThat(created.getUsername()).isEqualTo("newAdmin");
        assertThat(created.getPasswordHash()).isEqualTo("encoded");
        assertThat(created.getRole()).isEqualTo("VIEWER");
        assertThat(created.getCreatedAt()).isEqualTo("2026-07-26T22:45:00Z");
    }

    @Test
    void shouldRejectCreatingAdminUserWithTakenUsername() {
        // given
        given(adminUserRepository.existsByUsernameIgnoreCase("taken")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminUserService.create(new NewAdminUser("taken", "long-enough-password", "OWNER")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(adminUserRepository, never()).save(any(AdminUserData.class));
    }

    @Test
    void shouldSignOutAdminUserWhenRoleChanges() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder().id(adminUserId).username("viewer").role("VIEWER").build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));

        // when
        adminUserService.update(UUID.randomUUID(), adminUserId, new AdminUserChanges("viewer", "OWNER"));

        // then
        assertThat(adminUser.getRole()).isEqualTo("OWNER");
        verify(refreshTokenRepository).deleteByAdminUserId(adminUserId);
    }

    @Test
    void shouldKeepSessionsWhenOnlyUsernameChanges() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder().id(adminUserId).username("viewer").role("VIEWER").build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));
        given(adminUserRepository.existsByUsernameIgnoreCase("renamed")).willReturn(false);

        // when
        adminUserService.update(UUID.randomUUID(), adminUserId, new AdminUserChanges("renamed", "VIEWER"));

        // then
        assertThat(adminUser.getUsername()).isEqualTo("renamed");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void shouldRejectChangingOwnRole() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder().id(adminUserId).username("owner").role("OWNER").build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));

        // when & then
        assertThatThrownBy(() -> adminUserService.update(adminUserId, adminUserId, new AdminUserChanges("owner", "VIEWER")))
                .isInstanceOf(AdminUserStateException.class);
    }

    @Test
    void shouldRejectDeletingOwnAccount() {
        // given
        UUID adminUserId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> adminUserService.delete(adminUserId, adminUserId))
                .isInstanceOf(AdminUserStateException.class);
        verify(adminUserRepository, never()).delete(any(AdminUserData.class));
    }

    @Test
    void shouldDeleteAnotherAdminUser() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder().id(adminUserId).username("viewer").build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));

        // when
        AdminUserData deleted = adminUserService.delete(UUID.randomUUID(), adminUserId);

        // then
        assertThat(deleted).isSameAs(adminUser);
        verify(adminUserRepository).delete(adminUser);
    }

    @Test
    void shouldResetPasswordToGeneratedOneAndSignOutAccount() {
        // given
        UUID adminUserId = UUID.randomUUID();
        AdminUserData adminUser = AdminUserData.builder().id(adminUserId).username("viewer").passwordHash("old").build();
        given(adminUserRepository.findForUpdateById(adminUserId)).willReturn(Optional.of(adminUser));
        given(passwordGenerator.generate()).willReturn("temporary-password");
        given(passwordEncoder.encode("temporary-password")).willReturn("temporary-hash");

        // when
        PasswordReset reset = adminUserService.resetPassword(adminUserId);

        // then
        assertThat(reset.temporaryPassword()).isEqualTo("temporary-password");
        assertThat(adminUser.getPasswordHash()).isEqualTo("temporary-hash");
        verify(refreshTokenRepository).deleteByAdminUserId(adminUserId);
        verify(loginAttemptTracker).reset("viewer");
    }
}
