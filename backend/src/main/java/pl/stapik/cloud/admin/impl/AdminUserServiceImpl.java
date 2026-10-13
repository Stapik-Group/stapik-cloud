package pl.stapik.cloud.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.AdminUserService;
import pl.stapik.cloud.admin.PasswordVerificationException;
import pl.stapik.cloud.admin.RefreshTokenRepository;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;
import pl.stapik.cloud.security.ratelimit.LoginAttemptTracker;

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
    @Auditing(action = AuditAction.ADMIN_PASSWORD_CHANGED, details = "'Password changed'")
    @Override
    public void changePassword(UUID adminUserId, UUID currentSessionId, String currentPassword, String newPassword) {
        AdminUserData adminUser = adminUserRepository.findForUpdateById(adminUserId)
                .orElseThrow(() -> new NoSuchElementException("Admin user not found: " + adminUserId));

        loginAttemptTracker.checkNotLocked(adminUser.getUsername());
        if (!passwordEncoder.matches(currentPassword, adminUser.getPasswordHash())) {
            loginAttemptTracker.recordFailure(adminUser.getUsername());
            throw new PasswordVerificationException();
        }

        adminUser.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserRepository.save(adminUser);
        refreshTokenRepository.deleteByAdminUserIdAndSessionIdNot(adminUserId, currentSessionId);
    }
}
