package pl.stapik.cloud.admin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.admin.AdminUserRepository;
import pl.stapik.cloud.admin.AdminUserService;
import pl.stapik.cloud.admin.dto.Credentials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

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
}
