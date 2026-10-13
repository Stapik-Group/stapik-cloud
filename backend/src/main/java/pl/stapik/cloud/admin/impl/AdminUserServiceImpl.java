package pl.stapik.cloud.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.AdminUserService;
import pl.stapik.cloud.admin.AdminUserStateException;
import pl.stapik.cloud.admin.PasswordGenerator;
import pl.stapik.cloud.admin.PasswordVerificationException;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.dto.AdminUserChanges;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.admin.dto.NewAdminUser;
import pl.stapik.cloud.admin.dto.PasswordReset;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.common.error.DuplicateResourceException;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptTracker loginAttemptTracker;
    private final PasswordGenerator passwordGenerator;
    private final Clock clock;

    @Override
    public Optional<AdminUserData> authenticate(Credentials credentials) {
        return adminUserRepository.findByUsername(credentials.getUsername())
                .filter(user -> passwordEncoder.matches(credentials.getPassword(), user.getPasswordHash()));
    }

    @Override
    public Optional<AdminUserData> findById(UUID adminUserId) {
        return adminUserRepository.findById(adminUserId);
    }

    @Override
    public List<AdminUserData> findAll() {
        return adminUserRepository.findAll(Sort.by("username"));
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_USER_CREATED, details = "'Admin user ' + #result.username + ' created'")
    @Override
    public AdminUserData create(NewAdminUser newAdminUser) {
        requireUsernameAvailable(newAdminUser.username());

        return adminUserRepository.save(AdminUserData.builder()
                .username(newAdminUser.username())
                .passwordHash(passwordEncoder.encode(newAdminUser.password()))
                .role(newAdminUser.role())
                .createdAt(Instant.now(clock))
                .build());
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_USER_UPDATED, details = "'Admin user ' + #result.username + ' updated'")
    @Override
    public AdminUserData update(UUID actingAdminUserId, UUID adminUserId, AdminUserChanges changes) {
        AdminUserData adminUser = requireForUpdate(adminUserId);

        boolean roleChanged = !adminUser.getRole().equals(changes.role());
        if (roleChanged && adminUserId.equals(actingAdminUserId)) {
            throw new AdminUserStateException("You cannot change your own role");
        }
        if (!adminUser.getUsername().equalsIgnoreCase(changes.username())) {
            requireUsernameAvailable(changes.username());
        }

        adminUser.setUsername(changes.username());
        adminUser.setRole(changes.role());
        adminUserRepository.save(adminUser);
        if (roleChanged) {
            refreshTokenRepository.deleteByAdminUserId(adminUserId);
        }
        return adminUser;
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_USER_DELETED, details = "'Admin user ' + #result.username + ' deleted'")
    @Override
    public AdminUserData delete(UUID actingAdminUserId, UUID adminUserId) {
        if (adminUserId.equals(actingAdminUserId)) {
            throw new AdminUserStateException("You cannot delete your own account");
        }

        AdminUserData adminUser = requireForUpdate(adminUserId);
        adminUserRepository.delete(adminUser);
        return adminUser;
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_PASSWORD_RESET, details = "'Password reset for ' + #result.username()")
    @Override
    public PasswordReset resetPassword(UUID adminUserId) {
        AdminUserData adminUser = requireForUpdate(adminUserId);

        String temporaryPassword = passwordGenerator.generate();
        adminUser.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        adminUserRepository.save(adminUser);
        refreshTokenRepository.deleteByAdminUserId(adminUserId);
        loginAttemptTracker.reset(adminUser.getUsername());
        return new PasswordReset(adminUser.getUsername(), temporaryPassword);
    }

    @Transactional
    @Auditing(action = AuditAction.ADMIN_PASSWORD_CHANGED, details = "'Password changed'")
    @Override
    public void changePassword(UUID adminUserId, UUID currentSessionId, String currentPassword, String newPassword) {
        AdminUserData adminUser = requireForUpdate(adminUserId);

        loginAttemptTracker.checkNotLocked(adminUser.getUsername());
        if (!passwordEncoder.matches(currentPassword, adminUser.getPasswordHash())) {
            loginAttemptTracker.recordFailure(adminUser.getUsername());
            throw new PasswordVerificationException();
        }

        adminUser.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserRepository.save(adminUser);
        refreshTokenRepository.deleteByAdminUserIdAndSessionIdNot(adminUserId, currentSessionId);
    }

    private AdminUserData requireForUpdate(UUID adminUserId) {
        return adminUserRepository.findForUpdateById(adminUserId)
                .orElseThrow(() -> new NoSuchElementException("Admin user not found: " + adminUserId));
    }

    private void requireUsernameAvailable(String username) {
        if (adminUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("admin_user_username_key", "Admin user with this username already exists");
        }
    }
}
