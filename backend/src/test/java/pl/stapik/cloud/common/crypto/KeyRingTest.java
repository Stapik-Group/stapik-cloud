package pl.stapik.cloud.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyRingTest {

    @Test
    void shouldExposeActiveAndOlderKeys() {
        KeyRing keyRing = TestEncryption.keyRing(2, TestEncryption.keyEntry(1, 1), TestEncryption.keyEntry(2, 2));

        assertThat(keyRing.activeKeyId()).isEqualTo(2);
        assertThat(keyRing.active().getEncoded()).containsOnly(2);
        assertThat(keyRing.get(1).getEncoded()).containsOnly(1);
    }

    @Test
    void shouldRejectMissingActiveKey() {
        assertThatThrownBy(() -> TestEncryption.keyRing(3, TestEncryption.keyEntry(1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
    }

    @Test
    void shouldRejectUnknownKeyOnLookup() {
        KeyRing keyRing = TestEncryption.keyRing();

        assertThatThrownBy(() -> keyRing.get(9)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void shouldRejectKeyOfWrongLength() {
        assertThatThrownBy(() -> TestEncryption.keyRing(1, "1:AAAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void shouldRejectDuplicatedKeyId() {
        assertThatThrownBy(() -> TestEncryption.keyRing(1, TestEncryption.keyEntry(1, 1), TestEncryption.keyEntry(1, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void shouldRejectMalformedEntries() {
        assertThatThrownBy(() -> TestEncryption.keyRing(1, "no-separator")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestEncryption.keyRing(1, "x:AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestEncryption.keyRing(1, "1:***")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectMissingKeys() {
        assertThatThrownBy(() -> new KeyRing(new EncryptionProperties(1, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new KeyRing(new EncryptionProperties(1, List.of())))
                .isInstanceOf(IllegalStateException.class);
    }
}
