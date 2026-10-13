package pl.stapik.cloud.admin.totp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupCodeGeneratorTest {

    private final BackupCodeGenerator generator = new BackupCodeGenerator();

    @Test
    void shouldGenerateTwoGroupsOfUnambiguousCharacters() {
        for (int attempt = 0; attempt < 200; attempt++) {
            assertThat(generator.generate()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{5}-[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{5}");
        }
    }

    @Test
    void shouldGenerateDifferentCodes() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void shouldCanonicalizeUserInput() {
        assertThat(generator.canonical("abcde-fghjk")).isEqualTo("ABCDEFGHJK");
        assertThat(generator.canonical(" ABCDE FGHJK ")).isEqualTo("ABCDEFGHJK");
        assertThat(generator.canonical(null)).isEmpty();
    }

    @Test
    void shouldTellBackupCodesFromTotpCodes() {
        assertThat(generator.isBackupCode(generator.canonical(generator.generate()))).isTrue();
        assertThat(generator.isBackupCode("123456")).isFalse();
    }
}
