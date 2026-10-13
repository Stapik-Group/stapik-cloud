package pl.stapik.cloud.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTwoFactorResetRunner implements ApplicationRunner {

    static final String OPTION = "reset-two-factor";
    static final int EXIT_OK = 0;
    static final int EXIT_FAILED = 1;

    private final AdminUserRepository adminUserRepository;
    private final AdminTwoFactorService adminTwoFactorService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }

        int exitCode = resetTwoFactor(args);
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    int resetTwoFactor(ApplicationArguments args) {
        List<String> usernames = args.getOptionValues(OPTION);
        if (usernames == null || usernames.size() != 1 || usernames.getFirst().isBlank()) {
            log.error("Usage: --{}=<username>", OPTION);
            return EXIT_FAILED;
        }

        String username = usernames.getFirst();
        return adminUserRepository.findByUsername(username)
                .map(adminUser -> {
                    adminTwoFactorService.reset(adminUser.getId());
                    log.info("Two-factor authentication reset for {}", username);
                    return EXIT_OK;
                })
                .orElseGet(() -> {
                    log.error("Admin user not found: {}", username);
                    return EXIT_FAILED;
                });
    }
}
