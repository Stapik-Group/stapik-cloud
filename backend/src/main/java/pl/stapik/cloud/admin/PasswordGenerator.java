package pl.stapik.cloud.admin;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasswordGenerator {

    public static final int PASSWORD_LENGTH = 20;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder();
        for (int index = 0; index < PASSWORD_LENGTH; index++) {
            password.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
