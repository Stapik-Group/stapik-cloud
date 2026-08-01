package pl.stapik.cloud.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.AdminUserService;
import pl.stapik.cloud.admin.dto.Credentials;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Auditing(action = AuditAction.ADMIN_LOGIN_SUCCEEDED)
    public Optional<AdminUserData> authenticate(Credentials credentials) {
        return adminUserRepository.findByUsername(credentials.getUsername())
                .filter(user -> passwordEncoder.matches(credentials.getPassword(), user.getPasswordHash()));
    }
}
