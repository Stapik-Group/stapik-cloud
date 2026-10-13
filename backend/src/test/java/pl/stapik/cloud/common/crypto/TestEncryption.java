package pl.stapik.cloud.common.crypto;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public final class TestEncryption {

    private TestEncryption() {
    }

    public static String keyEntry(int keyId, int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return keyId + ":" + Base64.getEncoder().encodeToString(key);
    }

    public static KeyRing keyRing(int activeKeyId, String... entries) {
        return new KeyRing(new EncryptionProperties(activeKeyId, List.of(entries)));
    }

    public static KeyRing keyRing() {
        return keyRing(1, keyEntry(1, 1));
    }

    public static EnvelopeCipher envelopeCipher() {
        return new EnvelopeCipher(keyRing());
    }

    public static ContentEncryptor contentEncryptor() {
        KeyRing keyRing = keyRing();
        return new ContentEncryptor(new EnvelopeCipher(keyRing), keyRing);
    }
}
