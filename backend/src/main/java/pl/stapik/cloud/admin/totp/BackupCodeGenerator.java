package pl.stapik.cloud.admin.totp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class BackupCodeGenerator {

    public static final int CODE_LENGTH = 10;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GROUP_LENGTH = CODE_LENGTH / 2;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < CODE_LENGTH; index++) {
            if (index == GROUP_LENGTH) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public String canonical(String input) {
        return input == null ? "" : input.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public boolean isBackupCode(String canonical) {
        return canonical.length() == CODE_LENGTH;
    }
}
