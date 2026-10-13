package pl.stapik.cloud.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import pl.stapik.cloud.admin.data.AdminUserData;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTwoFactorResetRunnerTest {

    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AdminTwoFactorService adminTwoFactorService;
    @Mock
    private ConfigurableApplicationContext applicationContext;

    @Test
    void shouldResetTwoFactorOfNamedUser() {
        UUID adminUserId = UUID.randomUUID();
        when(adminUserRepository.findByUsername("alice"))
                .thenReturn(Optional.of(AdminUserData.builder().id(adminUserId).username("alice").build()));

        int exitCode = runner().resetTwoFactor(new DefaultApplicationArguments("--reset-two-factor=alice"));

        assertThat(exitCode).isEqualTo(AdminTwoFactorResetRunner.EXIT_OK);
        verify(adminTwoFactorService).reset(adminUserId);
    }

    @Test
    void shouldFailForUnknownUser() {
        when(adminUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        int exitCode = runner().resetTwoFactor(new DefaultApplicationArguments("--reset-two-factor=ghost"));

        assertThat(exitCode).isEqualTo(AdminTwoFactorResetRunner.EXIT_FAILED);
        verify(adminTwoFactorService, never()).reset(any());
    }

    @Test
    void shouldFailWithoutUsername() {
        int exitCode = runner().resetTwoFactor(new DefaultApplicationArguments("--reset-two-factor"));

        assertThat(exitCode).isEqualTo(AdminTwoFactorResetRunner.EXIT_FAILED);
        verifyNoInteractions(adminUserRepository, adminTwoFactorService);
    }

    @Test
    void shouldDoNothingWithoutOption() {
        runner().run(new DefaultApplicationArguments("--other=1"));

        verifyNoInteractions(adminUserRepository, adminTwoFactorService, applicationContext);
    }

    private AdminTwoFactorResetRunner runner() {
        return new AdminTwoFactorResetRunner(adminUserRepository, adminTwoFactorService, applicationContext);
    }
}
