package pl.stapik.cloud.common.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class EnvelopeCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int DATA_KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    public static final int SEAL_OVERHEAD_BYTES = IV_BYTES + TAG_BITS / 8;
    public static final int WRAPPED_KEY_BYTES = Integer.BYTES + SEAL_OVERHEAD_BYTES + DATA_KEY_BYTES;

    private final KeyRing keyRing;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretKey newDataKey() {
        byte[] keyBytes = new byte[DATA_KEY_BYTES];
        secureRandom.nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    public byte[] wrap(SecretKey dataKey) {
        byte[] keyId = ByteBuffer.allocate(Integer.BYTES).putInt(keyRing.activeKeyId()).array();
        byte[] sealed = seal(keyRing.active(), dataKey.getEncoded(), DATA_KEY_BYTES, keyId);
        return ByteBuffer.allocate(WRAPPED_KEY_BYTES).put(keyId).put(sealed).array();
    }

    public SecretKey unwrap(byte[] wrappedKey) {
        int keyId = keyIdOf(wrappedKey);
        byte[] keyIdBytes = Arrays.copyOfRange(wrappedKey, 0, Integer.BYTES);
        byte[] sealed = Arrays.copyOfRange(wrappedKey, Integer.BYTES, WRAPPED_KEY_BYTES);
        return new SecretKeySpec(open(keyRing.get(keyId), sealed, sealed.length, keyIdBytes), KEY_ALGORITHM);
    }

    public byte[] rewrap(byte[] wrappedKey) {
        return wrap(unwrap(wrappedKey));
    }

    public int keyIdOf(byte[] wrappedKey) {
        if (wrappedKey.length != WRAPPED_KEY_BYTES) {
            throw new EncryptionException("Malformed wrapped key");
        }
        return ByteBuffer.wrap(wrappedKey).getInt();
    }

    public boolean isWrappedWithActiveKey(byte[] wrappedKey) {
        return keyIdOf(wrappedKey) == keyRing.activeKeyId();
    }

    public byte[] seal(SecretKey key, byte[] plaintext, int length, byte[] associatedData) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData);

            byte[] sealed = new byte[IV_BYTES + cipher.getOutputSize(length)];
            System.arraycopy(iv, 0, sealed, 0, IV_BYTES);
            cipher.doFinal(plaintext, 0, length, sealed, IV_BYTES);
            return sealed;
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public byte[] open(SecretKey key, byte[] sealed, int length, byte[] associatedData) {
        if (length < SEAL_OVERHEAD_BYTES) {
            throw new EncryptionException("Encrypted data is too short");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(sealed, IV_BYTES, length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Decryption failed: the data is corrupted or the key is wrong", e);
        }
    }
}
