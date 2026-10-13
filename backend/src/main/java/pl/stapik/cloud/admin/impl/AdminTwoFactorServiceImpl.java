package pl.stapik.cloud.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.admin.AdminBackupCodeRepository;
import pl.stapik.cloud.admin.AdminTwoFactorService;
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
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.common.crypto.HashingService;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminTwoFactorServiceImpl implements AdminTwoFactorService {

    private static final int BACKUP_CODE_COUNT = 10;

    private final AdminUserRepository adminUserRepository;
    private final AdminBackupCodeRepository backupCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TotpService totpService;
    private final QrCodeRenderer qrCodeRenderer;
    private final BackupCodeGenerator backupCodeGenerator;
    private final HashingService hashingService;
    private final LoginAttemptTracker loginAttemptTracker;
    private final Clock clock;

    @Transactional(readOnly = true)
    @Override
    public AccountInfo describe(UUID adminUserId) {
        AdminUserData adminUser = adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> adminUserNotFound(adminUserId));
        long backupCodesRemaining = adminUser.isTotpEnabled()
                ? backupCodeRepository.countByAdminUserIdAndUsedAtIsNull(adminUserId)
                : 0;
        return new AccountInfo(adminUser.getUsername(), adminUser.getRole(), adminUser.isTotpEnabled(), backupCodesRemaining);
    }

    @Transactional
    @Override
    public TwoFactorSetup beginSetup(UUID adminUserId) {
        AdminUserData adminUser = requireForUpdate(adminUserId);
        if (adminUser.isTotpEnabled()) {
            throw new TwoFactorStateException("Two-factor authentication is already enabled");
        }

        String secret = totpService.newSecret();
        adminUser.setTotpSecret(secret);
        adminUser.setTotpLastUsedStep(null);
        adminUserRepository.save(adminUser);

        String provisioningUri = totpService.provisioningUri(adminUser.getUsername(), secret);
        return new TwoFactorSetup(secret, provisioningUri, qrCodeRenderer.toSvg(provisioningUri));
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_TWO_FACTOR_ENABLED)
    @Override
    public List<String> enable(UUID adminUserId, String verificationCode) {
        AdminUserData adminUser = requireForUpdate(adminUserId);
        if (adminUser.isTotpEnabled()) {
            throw new TwoFactorStateException("Two-factor authentication is already enabled");
        }
        if (adminUser.getTotpSecret() == null) {
            throw new TwoFactorStateException("Start the two-factor setup first");
        }

        OptionalLong step = totpService.findMatchingStep(adminUser.getTotpSecret(), verificationCode);
        if (step.isEmpty()) {
            throw new TwoFactorVerificationException();
        }

        adminUser.setTotpEnabled(true);
        adminUser.setTotpLastUsedStep(step.getAsLong());
        adminUserRepository.save(adminUser);
        return issueBackupCodes(adminUserId);
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_TWO_FACTOR_DISABLED)
    @Override
    public void disable(UUID adminUserId, String password, String verificationCode) {
        AdminUserData adminUser = requireEnabledAndConfirmed(adminUserId, password, verificationCode);
        clearTwoFactor(adminUser);
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_BACKUP_CODES_REGENERATED)
    @Override
    public List<String> regenerateBackupCodes(UUID adminUserId, String password, String verificationCode) {
        requireEnabledAndConfirmed(adminUserId, password, verificationCode);
        return issueBackupCodes(adminUserId);
    }

    @Transactional
    @Override
    public boolean verifyLoginCode(UUID adminUserId, String verificationCode) {
        AdminUserData adminUser = requireForUpdate(adminUserId);
        return adminUser.isTotpEnabled() && acceptCode(adminUser, verificationCode);
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_TWO_FACTOR_RESET, details = "'Two-factor authentication reset for ' + #result.username")
    @Override
    public AdminUserData reset(UUID adminUserId) {
        AdminUserData adminUser = requireForUpdate(adminUserId);
        clearTwoFactor(adminUser);
        refreshTokenRepository.deleteByAdminUserId(adminUserId);
        return adminUser;
    }

    private AdminUserData requireEnabledAndConfirmed(UUID adminUserId, String password, String verificationCode) {
        AdminUserData adminUser = requireForUpdate(adminUserId);
        if (!adminUser.isTotpEnabled()) {
            throw new TwoFactorStateException("Two-factor authentication is not enabled");
        }

        loginAttemptTracker.checkNotLocked(adminUser.getUsername());
        boolean confirmed = hashingService.matches(password, adminUser.getPasswordHash())
                && acceptCode(adminUser, verificationCode);
        if (!confirmed) {
            loginAttemptTracker.recordFailure(adminUser.getUsername());
            throw new TwoFactorVerificationException();
        }
        return adminUser;
    }

    private boolean acceptCode(AdminUserData adminUser, String verificationCode) {
        OptionalLong step = totpService.findMatchingStep(adminUser.getTotpSecret(), verificationCode);
        if (step.isPresent()) {
            return acceptStep(adminUser, step.getAsLong());
        }
        return consumeBackupCode(adminUser.getId(), verificationCode);
    }

    private boolean acceptStep(AdminUserData adminUser, long step) {
        Long lastUsedStep = adminUser.getTotpLastUsedStep();
        if (lastUsedStep != null && step <= lastUsedStep) {
            return false;
        }

        adminUser.setTotpLastUsedStep(step);
        adminUserRepository.save(adminUser);
        return true;
    }

    private boolean consumeBackupCode(UUID adminUserId, String verificationCode) {
        String canonical = backupCodeGenerator.canonical(verificationCode);
        if (!backupCodeGenerator.isBackupCode(canonical)) {
            return false;
        }

        return backupCodeRepository.findByAdminUserIdAndUsedAtIsNull(adminUserId).stream()
                .filter(stored -> hashingService.matches(canonical, stored.getCodeHash()))
                .findFirst()
                .map(stored -> {
                    stored.setUsedAt(clock.instant());
                    backupCodeRepository.save(stored);
                    return true;
                })
                .orElse(false);
    }

    private List<String> issueBackupCodes(UUID adminUserId) {
        backupCodeRepository.deleteByAdminUserId(adminUserId);

        List<String> codes = Stream.generate(backupCodeGenerator::generate).limit(BACKUP_CODE_COUNT).toList();
        backupCodeRepository.saveAll(codes.stream()
                .map(code -> AdminBackupCodeData.builder()
                        .adminUserId(adminUserId)
                        .codeHash(hashingService.hash(backupCodeGenerator.canonical(code)))
                        .build())
                .toList());
        return codes;
    }

    private void clearTwoFactor(AdminUserData adminUser) {
        adminUser.setTotpSecret(null);
        adminUser.setTotpEnabled(false);
        adminUser.setTotpLastUsedStep(null);
        adminUserRepository.save(adminUser);
        backupCodeRepository.deleteByAdminUserId(adminUser.getId());
    }

    private AdminUserData requireForUpdate(UUID adminUserId) {
        return adminUserRepository.findForUpdateById(adminUserId)
                .orElseThrow(() -> adminUserNotFound(adminUserId));
    }

    private NoSuchElementException adminUserNotFound(UUID adminUserId) {
        return new NoSuchElementException("Admin user not found: " + adminUserId);
    }
}
