package pl.stapik.cloud.common.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ApiKeyHasher {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final HashingService legacyHashingService;

    public String hash(String rawKey) {
        return HexFormat.of().formatHex(sha256(rawKey));
    }

    public boolean matches(String rawKey, String storedHash) {
        if (isLegacy(storedHash)) {
            return legacyHashingService.matches(rawKey, storedHash);
        }
        return MessageDigest.isEqual(
                hash(rawKey).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    public boolean isLegacy(String storedHash) {
        return !SHA256_HEX.matcher(storedHash).matches();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
