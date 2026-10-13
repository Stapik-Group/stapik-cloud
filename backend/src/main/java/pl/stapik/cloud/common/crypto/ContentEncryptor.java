package pl.stapik.cloud.common.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ContentEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final String SEPARATOR = ":";
    private static final int PARTS = 5;
    private static final byte[] NO_ASSOCIATED_DATA = new byte[0];

    private final EnvelopeCipher envelopeCipher;
    private final KeyRing keyRing;

    public static boolean isEncrypted(String stored) {
        return stored.startsWith(PREFIX);
    }

    public String currentPrefix() {
        return PREFIX + keyRing.activeKeyId() + SEPARATOR;
    }

    public String encrypt(String plaintext) {
        SecretKey dataKey = envelopeCipher.newDataKey();
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] sealed = envelopeCipher.seal(dataKey, plainBytes, plainBytes.length, NO_ASSOCIATED_DATA);
        return format(envelopeCipher.wrap(dataKey), sealed);
    }

    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }

        Envelope envelope = parse(stored);
        SecretKey dataKey = envelopeCipher.unwrap(envelope.wrappedKey());
        byte[] plainBytes = envelopeCipher.open(dataKey, envelope.sealed(), envelope.sealed().length, NO_ASSOCIATED_DATA);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    public String rewrap(String stored) {
        if (!isEncrypted(stored)) {
            return encrypt(stored);
        }

        Envelope envelope = parse(stored);
        return format(envelopeCipher.rewrap(envelope.wrappedKey()), envelope.sealed());
    }

    private record Envelope(byte[] wrappedKey, byte[] sealed) { }

    private String format(byte[] wrappedKey, byte[] sealed) {
        return PREFIX + envelopeCipher.keyIdOf(wrappedKey)
                + SEPARATOR + Base64.getEncoder().encodeToString(wrappedKey)
                + SEPARATOR + Base64.getEncoder().encodeToString(sealed);
    }

    private Envelope parse(String stored) {
        String[] parts = stored.split(SEPARATOR, PARTS);
        if (parts.length != PARTS) {
            throw new EncryptionException("Malformed encrypted content");
        }

        try {
            byte[] wrappedKey = Base64.getDecoder().decode(parts[3]);
            byte[] sealed = Base64.getDecoder().decode(parts[4]);
            if (!parts[2].equals(String.valueOf(envelopeCipher.keyIdOf(wrappedKey)))) {
                throw new EncryptionException("Malformed encrypted content");
            }
            return new Envelope(wrappedKey, sealed);
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("Malformed encrypted content", e);
        }
    }
}
