package pl.stapik.cloud.admin.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.admin.AdminBackupCodeRepository;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.TwoFactorStateException;
import pl.stapik.cloud.admin.TwoFactorVerificationException;
import pl.stapik.cloud.admin.data.AdminBackupCodeData;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.dto.AccountInfo;
import pl.stapik.cloud.admin.dto.TwoFactorSetup;
import pl.stapik.cloud.admin.totp.BackupCodeGenerator;
import pl.stapik.cloud.admin.totp.QrCodeRenderer;
import pl.stapik.cloud.admin.totp.TotpService;
import pl.stapik.cloud.common.crypto.HashingService;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTwoFactorServiceImplTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_111_111_109L);
    private static final long CURRENT_STEP = NOW.getEpochSecond() / 30;
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AdminBackupCodeRepository backupCodeRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private HashingService hashingService;
    @Mock
    private LoginAttemptTracker loginAttemptTracker;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TotpService totpService = new TotpService(clock, "Stapik Cloud");
    private final BackupCodeGenerator backupCodeGenerator = new BackupCodeGenerator();

    private AdminTwoFactorServiceImpl service;
    private AdminUserData adminUser;

    @BeforeEach
    void setUp() {
        service = new AdminTwoFactorServiceImpl(adminUserRepository, backupCodeRepository, refreshTokenRepository,
                totpService, new QrCodeRenderer(), backupCodeGenerator, hashingService, loginAttemptTracker, clock);
        adminUser = AdminUserData.builder()
                .id(UUID.randomUUID())
                .username("owner")
                .passwordHash("password-hash")
                .role("OWNER")
                .build();
        lenient().when(adminUserRepository.findForUpdateById(adminUser.getId())).thenReturn(Optional.of(adminUser));
    }

    @Test
    void shouldStoreSecretAndReturnProvisioningDataOnSetup() {
        TwoFactorSetup setup = service.beginSetup(adminUser.getId());

        assertThat(adminUser.getTotpSecret()).isEqualTo(setup.secret());
        assertThat(adminUser.isTotpEnabled()).isFalse();
        assertThat(setup.provisioningUri()).startsWith("otpauth://totp/").contains("secret=" + setup.secret());
        assertThat(setup.qrCodeSvg()).contains("<svg");
    }

    @Test
    void shouldRejectSetupWhenAlreadyEnabled() {
        adminUser.setTotpEnabled(true);

        assertThatThrownBy(() -> service.beginSetup(adminUser.getId())).isInstanceOf(TwoFactorStateException.class);
    }

    @Test
    void shouldEnableAndIssueTenBackupCodesWhenCodeIsValid() {
        adminUser.setTotpSecret(SECRET);
        when(hashingService.hash(anyString())).thenReturn("backup-hash");

        List<String> codes = service.enable(adminUser.getId(), totpService.codeFor(SECRET, NOW));

        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(adminUser.isTotpEnabled()).isTrue();
        assertThat(adminUser.getTotpLastUsedStep()).isEqualTo(CURRENT_STEP);
        verify(backupCodeRepository).deleteByAdminUserId(adminUser.getId());
        ArgumentCaptor<List<AdminBackupCodeData>> saved = ArgumentCaptor.captor();
        verify(backupCodeRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(10).allSatisfy(code -> {
            assertThat(code.getAdminUserId()).isEqualTo(adminUser.getId());
            assertThat(code.getCodeHash()).isEqualTo("backup-hash");
        });
    }

    @Test
    void shouldNotEnableWhenCodeIsInvalid() {
        adminUser.setTotpSecret(SECRET);

        assertThatThrownBy(() -> service.enable(adminUser.getId(), "000000"))
                .isInstanceOf(TwoFactorVerificationException.class);
        assertThat(adminUser.isTotpEnabled()).isFalse();
    }

    @Test
    void shouldNotEnableWithoutSetup() {
        assertThatThrownBy(() -> service.enable(adminUser.getId(), "123456"))
                .isInstanceOf(TwoFactorStateException.class);
    }

    @Test
    void shouldAcceptLoginCodeOnceAndRejectReplay() {
        enableTwoFactor();
        String code = totpService.codeFor(SECRET, NOW);

        assertThat(service.verifyLoginCode(adminUser.getId(), code)).isTrue();
        assertThat(service.verifyLoginCode(adminUser.getId(), code)).isFalse();
        assertThat(adminUser.getTotpLastUsedStep()).isEqualTo(CURRENT_STEP);
    }

    @Test
    void shouldAcceptLaterStepAfterEarlierOne() {
        enableTwoFactor();
        adminUser.setTotpLastUsedStep(CURRENT_STEP - 1);

        assertThat(service.verifyLoginCode(adminUser.getId(), totpService.codeFor(SECRET, NOW))).isTrue();
    }

    @Test
    void shouldRejectLoginCodeWhenTwoFactorIsDisabled() {
        adminUser.setTotpSecret(SECRET);

        assertThat(service.verifyLoginCode(adminUser.getId(), totpService.codeFor(SECRET, NOW))).isFalse();
    }

    @Test
    void shouldConsumeBackupCodeOnlyOnce() {
        enableTwoFactor();
        AdminBackupCodeData stored = AdminBackupCodeData.builder().adminUserId(adminUser.getId()).codeHash("h").build();
        when(backupCodeRepository.findByAdminUserIdAndUsedAtIsNull(adminUser.getId()))
                .thenReturn(List.of(stored), List.of());
        when(hashingService.matches("ABCDE12345", "h")).thenReturn(true);

        assertThat(service.verifyLoginCode(adminUser.getId(), "abcde-12345")).isTrue();
        assertThat(stored.getUsedAt()).isEqualTo(NOW);
        assertThat(service.verifyLoginCode(adminUser.getId(), "abcde-12345")).isFalse();
    }

    @Test
    void shouldNotTouchBackupCodesForWrongSixDigitCode() {
        enableTwoFactor();

        assertThat(service.verifyLoginCode(adminUser.getId(), "000000")).isFalse();
        verify(backupCodeRepository, never()).findByAdminUserIdAndUsedAtIsNull(any());
    }

    @Test
    void shouldDisableWhenPasswordAndCodeAreCorrect() {
        enableTwoFactor();
        when(hashingService.matches("secret", "password-hash")).thenReturn(true);

        service.disable(adminUser.getId(), "secret", totpService.codeFor(SECRET, NOW));

        assertThat(adminUser.isTotpEnabled()).isFalse();
        assertThat(adminUser.getTotpSecret()).isNull();
        verify(backupCodeRepository).deleteByAdminUserId(adminUser.getId());
    }

    @Test
    void shouldRecordFailureAndKeepTwoFactorWhenPasswordIsWrong() {
        enableTwoFactor();
        when(hashingService.matches("wrong", "password-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.disable(adminUser.getId(), "wrong", totpService.codeFor(SECRET, NOW)))
                .isInstanceOf(TwoFactorVerificationException.class);

        assertThat(adminUser.isTotpEnabled()).isTrue();
        verify(loginAttemptTracker).recordFailure("owner");
    }

    @Test
    void shouldRegenerateBackupCodesAfterConfirmation() {
        enableTwoFactor();
        when(hashingService.matches("secret", "password-hash")).thenReturn(true);
        when(hashingService.hash(anyString())).thenReturn("backup-hash");

        List<String> codes = service.regenerateBackupCodes(adminUser.getId(), "secret", totpService.codeFor(SECRET, NOW));

        assertThat(codes).hasSize(10);
        verify(backupCodeRepository).deleteByAdminUserId(adminUser.getId());
    }

    @Test
    void shouldRejectRegenerationWhenTwoFactorIsDisabled() {
        assertThatThrownBy(() -> service.regenerateBackupCodes(adminUser.getId(), "secret", "123456"))
                .isInstanceOf(TwoFactorStateException.class);
        verify(hashingService, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldResetStateAndRevokeRefreshTokens() {
        enableTwoFactor();
        adminUser.setTotpLastUsedStep(CURRENT_STEP);

        AdminUserData result = service.reset(adminUser.getId());

        assertThat(result.getUsername()).isEqualTo("owner");
        assertThat(adminUser.isTotpEnabled()).isFalse();
        assertThat(adminUser.getTotpSecret()).isNull();
        assertThat(adminUser.getTotpLastUsedStep()).isNull();
        verify(backupCodeRepository).deleteByAdminUserId(adminUser.getId());
        verify(refreshTokenRepository).deleteByAdminUserId(adminUser.getId());
    }

    @Test
    void shouldDescribeAccountWithRemainingBackupCodes() {
        enableTwoFactor();
        when(adminUserRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(backupCodeRepository.countByAdminUserIdAndUsedAtIsNull(adminUser.getId())).thenReturn(7L);

        AccountInfo info = service.describe(adminUser.getId());

        assertThat(info).isEqualTo(new AccountInfo("owner", "OWNER", true, 7));
    }

    @Test
    void shouldDescribeAccountWithoutTwoFactor() {
        when(adminUserRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        AccountInfo info = service.describe(adminUser.getId());

        assertThat(info.twoFactorEnabled()).isFalse();
        assertThat(info.backupCodesRemaining()).isZero();
        verify(backupCodeRepository, never()).countByAdminUserIdAndUsedAtIsNull(adminUser.getId());
    }

    private void enableTwoFactor() {
        adminUser.setTotpSecret(SECRET);
        adminUser.setTotpEnabled(true);
    }
}
