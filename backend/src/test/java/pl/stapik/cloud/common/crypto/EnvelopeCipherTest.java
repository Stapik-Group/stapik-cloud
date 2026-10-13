package pl.stapik.cloud.common.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCipherTest {

    private static final byte[] AAD = "context".getBytes(StandardCharsets.UTF_8);

    private final EnvelopeCipher cipher = TestEncryption.envelopeCipher();

    @Test
    void shouldOpenWhatWasSealed() {
        SecretKey key = cipher.newDataKey();
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] sealed = cipher.seal(key, plaintext, plaintext.length, AAD);

        assertThat(sealed).hasSize(plaintext.length + EnvelopeCipher.SEAL_OVERHEAD_BYTES);
        assertThat(cipher.open(key, sealed, sealed.length, AAD)).isEqualTo(plaintext);
    }

    @Test
    void shouldSealOnlyRequestedPrefixOfBuffer() {
        SecretKey key = cipher.newDataKey();
        byte[] buffer = "abcdef".getBytes(StandardCharsets.UTF_8);

        byte[] sealed = cipher.seal(key, buffer, 3, AAD);

        assertThat(cipher.open(key, sealed, sealed.length, AAD)).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldUseDifferentIvForEverySeal() {
        SecretKey key = cipher.newDataKey();
        byte[] plaintext = "same".getBytes(StandardCharsets.UTF_8);

        assertThat(cipher.seal(key, plaintext, plaintext.length, AAD))
                .isNotEqualTo(cipher.seal(key, plaintext, plaintext.length, AAD));
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        SecretKey key = cipher.newDataKey();
        byte[] sealed = cipher.seal(key, "secret".getBytes(StandardCharsets.UTF_8), 6, AAD);
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.open(key, sealed, sealed.length, AAD)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectOtherAssociatedData() {
        SecretKey key = cipher.newDataKey();
        byte[] sealed = cipher.seal(key, "secret".getBytes(StandardCharsets.UTF_8), 6, AAD);

        assertThatThrownBy(() -> cipher.open(key, sealed, sealed.length, new byte[0])).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectTooShortInput() {
        SecretKey key = cipher.newDataKey();

        assertThatThrownBy(() -> cipher.open(key, new byte[5], 5, AAD)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldUnwrapWhatWasWrapped() {
        SecretKey dataKey = cipher.newDataKey();

        byte[] wrapped = cipher.wrap(dataKey);

        assertThat(wrapped).hasSize(EnvelopeCipher.WRAPPED_KEY_BYTES);
        assertThat(cipher.unwrap(wrapped).getEncoded()).isEqualTo(dataKey.getEncoded());
    }

    @Test
    void shouldRejectTamperedWrappedKey() {
        byte[] wrapped = cipher.wrap(cipher.newDataKey());
        wrapped[wrapped.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.unwrap(wrapped)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectWrappedKeyOfWrongLength() {
        assertThatThrownBy(() -> cipher.unwrap(new byte[3])).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldMoveWrappedKeyToActiveKeyOnRewrap() {
        KeyRing oldRing = TestEncryption.keyRing(1, TestEncryption.keyEntry(1, 1));
        KeyRing rotatedRing = TestEncryption.keyRing(2, TestEncryption.keyEntry(1, 1), TestEncryption.keyEntry(2, 2));
        EnvelopeCipher oldCipher = new EnvelopeCipher(oldRing);
        EnvelopeCipher rotatedCipher = new EnvelopeCipher(rotatedRing);
        SecretKey dataKey = oldCipher.newDataKey();
        byte[] wrappedByOldKey = oldCipher.wrap(dataKey);

        byte[] rewrapped = rotatedCipher.rewrap(wrappedByOldKey);

        assertThat(rotatedCipher.isWrappedWithActiveKey(wrappedByOldKey)).isFalse();
        assertThat(rotatedCipher.isWrappedWithActiveKey(rewrapped)).isTrue();
        assertThat(rotatedCipher.unwrap(rewrapped).getEncoded()).isEqualTo(dataKey.getEncoded());
        assertThatThrownBy(() -> oldCipher.unwrap(rewrapped)).isInstanceOf(EncryptionException.class);
    }
}
