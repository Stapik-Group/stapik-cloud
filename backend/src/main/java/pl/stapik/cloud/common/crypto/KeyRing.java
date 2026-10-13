package pl.stapik.cloud.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KeyRing {

    private static final int KEY_BYTES = 32;
    private static final String ENTRY_SEPARATOR = ":";

    private final int activeKeyId;
    private final Map<Integer, SecretKey> keys;

    public KeyRing(EncryptionProperties properties) {
        this.activeKeyId = properties.activeKeyId();
        this.keys = parse(properties.keys() == null ? List.of() : properties.keys());

        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalStateException("Active encryption key " + activeKeyId + " is not configured");
        }
    }

    public int activeKeyId() {
        return activeKeyId;
    }

    public SecretKey active() {
        return keys.get(activeKeyId);
    }

    public SecretKey get(int keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new EncryptionException("Encryption key " + keyId + " is not configured");
        }
        return key;
    }

    private static Map<Integer, SecretKey> parse(List<String> entries) {
        Map<Integer, SecretKey> parsed = new HashMap<>();
        for (String entry : entries) {
            int separator = entry.indexOf(ENTRY_SEPARATOR);
            if (separator < 0) {
                throw new IllegalStateException("Encryption key entries must look like <id>:<base64 key>");
            }

            int keyId = parseKeyId(entry.substring(0, separator).strip());
            byte[] keyBytes = decode(keyId, entry.substring(separator + 1).strip());
            if (keyBytes.length != KEY_BYTES) {
                throw new IllegalStateException("Encryption key " + keyId + " must be " + KEY_BYTES + " bytes long");
            }
            if (parsed.put(keyId, new SecretKeySpec(keyBytes, "AES")) != null) {
                throw new IllegalStateException("Encryption key " + keyId + " is configured twice");
            }
        }
        return parsed;
    }

    private static int parseKeyId(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Encryption key id must be a number: " + text);
        }
    }

    private static byte[] decode(int keyId, String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encryption key " + keyId + " is not valid base64");
        }
    }
}
