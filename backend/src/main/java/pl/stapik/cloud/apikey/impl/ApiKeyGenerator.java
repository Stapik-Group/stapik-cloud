package pl.stapik.cloud.apikey.impl;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyGenerator {
    private static final int RAW_BYTES = 32;
    private static final int PREFIX_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public record GeneratedKey(String rawKey, String prefix) { }

    public GeneratedKey generate() {
        byte[] randomBytes = new byte[RAW_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GeneratedKey(rawKey, rawKey.substring(0, PREFIX_LENGTH));
    }
}
