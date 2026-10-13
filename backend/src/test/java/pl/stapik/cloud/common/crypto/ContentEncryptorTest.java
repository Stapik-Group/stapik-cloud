package pl.stapik.cloud.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentEncryptorTest {

    private final ContentEncryptor encryptor = TestEncryption.contentEncryptor();

    @Test
    void shouldRoundTripContent() {
        String stored = encryptor.encrypt("{\"title\": \"zażółć gęślą jaźń\"}");

        assertThat(ContentEncryptor.isEncrypted(stored)).isTrue();
        assertThat(stored).doesNotContain("zażółć");
        assertThat(encryptor.decrypt(stored)).isEqualTo("{\"title\": \"zażółć gęślą jaźń\"}");
    }

    @Test
    void shouldRoundTripEmptyContent() {
        assertThat(encryptor.decrypt(encryptor.encrypt(""))).isEmpty();
    }

    @Test
    void shouldProduceDifferentEnvelopeEachTime() {
        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    void shouldTagEnvelopeWithActiveKeyId() {
        assertThat(encryptor.encrypt("x")).startsWith(encryptor.currentPrefix());
        assertThat(encryptor.currentPrefix()).isEqualTo("enc:v1:1:");
    }

    @Test
    void shouldReturnLegacyPlaintextAsIs() {
        assertThat(encryptor.decrypt("{\"legacy\": true}")).isEqualTo("{\"legacy\": true}");
    }

    @Test
    void shouldEncryptLegacyPlaintextOnRewrap() {
        String rewrapped = encryptor.rewrap("legacy");

        assertThat(ContentEncryptor.isEncrypted(rewrapped)).isTrue();
        assertThat(encryptor.decrypt(rewrapped)).isEqualTo("legacy");
    }

    @Test
    void shouldKeepContentReadableAfterKeyRotation() {
        KeyRing oldRing = TestEncryption.keyRing(1, TestEncryption.keyEntry(1, 1));
        KeyRing rotatedRing = TestEncryption.keyRing(2, TestEncryption.keyEntry(1, 1), TestEncryption.keyEntry(2, 2));
        ContentEncryptor oldEncryptor = new ContentEncryptor(new EnvelopeCipher(oldRing), oldRing);
        ContentEncryptor rotatedEncryptor = new ContentEncryptor(new EnvelopeCipher(rotatedRing), rotatedRing);
        String storedByOldKey = oldEncryptor.encrypt("content");

        String rewrapped = rotatedEncryptor.rewrap(storedByOldKey);

        assertThat(rotatedEncryptor.decrypt(storedByOldKey)).isEqualTo("content");
        assertThat(rewrapped).startsWith("enc:v1:2:");
        assertThat(rotatedEncryptor.decrypt(rewrapped)).isEqualTo("content");
        assertThat(rewrapped.split(":")[4]).isEqualTo(storedByOldKey.split(":")[4]);
    }

    @Test
    void shouldRejectTamperedPayload() {
        String stored = encryptor.encrypt("content");
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectMalformedEnvelope() {
        assertThatThrownBy(() -> encryptor.decrypt("enc:v1:1:only-three")).isInstanceOf(EncryptionException.class);
        assertThatThrownBy(() -> encryptor.decrypt("enc:v1:1:***:***")).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectEnvelopeWhoseKeyIdLabelDoesNotMatchWrappedKey() {
        String[] parts = encryptor.encrypt("content").split(":");
        String relabeled = String.join(":", parts[0], parts[1], "7", parts[3], parts[4]);

        assertThatThrownBy(() -> encryptor.decrypt(relabeled)).isInstanceOf(EncryptionException.class);
    }
}
