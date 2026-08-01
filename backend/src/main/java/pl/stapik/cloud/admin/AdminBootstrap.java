package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.data.AdminUserData;
import pl.stapik.cloud.common.crypto.HashingService;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final HashingService hashingService;

    @Value("${stapik-cloud.admin-bootstrap.username:}")
    private String bootstrapUsername;

    @Value("${stapik-cloud.admin-bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        if (bootstrapUsername.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }

        AdminUserData adminUser = AdminUserData.builder()
                .username(bootstrapUsername)
                .passwordHash(hashingService.hash(bootstrapPassword))
                .role("OWNER")
                .createdAt(Instant.now())
                .build();

        adminUserRepository.save(adminUser);
    }
}
