package pl.stapik.cloud.asset.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.stapik.cloud.asset.storage.impl.AssetFileCipher;
import pl.stapik.cloud.common.crypto.EnvelopeCipher;
import pl.stapik.cloud.common.crypto.KeyRing;
import pl.stapik.cloud.common.crypto.TestEncryption;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AssetFileRotationTest {

    @TempDir
    Path root;

    @Test
    void shouldRewrapDataKeyWithoutTouchingContent() throws Exception {
        // given
        KeyRing oldRing = TestEncryption.keyRing(1, TestEncryption.keyEntry(1, 1));
        KeyRing rotatedRing = TestEncryption.keyRing(2, TestEncryption.keyEntry(1, 1), TestEncryption.keyEntry(2, 2));
        AssetFileCipher oldCipher = new AssetFileCipher(new EnvelopeCipher(oldRing));
        AssetFileCipher rotatedCipher = new AssetFileCipher(new EnvelopeCipher(rotatedRing));
        Path file = root.resolve("asset.bin");
        try (AssetFileCipher.Appender appender = oldCipher.create(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            appender.write(new ByteArrayInputStream("rotating".getBytes(StandardCharsets.UTF_8)), 100);
        }
        byte[] contentBefore = Files.readAllBytes(file);

        // when
        rotatedCipher.secure(file);

        // then
        byte[] contentAfter = Files.readAllBytes(file);
        int headerBytes = 8 + EnvelopeCipher.WRAPPED_KEY_BYTES;
        assertThat(Arrays.copyOfRange(contentAfter, headerBytes, contentAfter.length))
                .isEqualTo(Arrays.copyOfRange(contentBefore, headerBytes, contentBefore.length));
        assertThat(contentAfter).isNotEqualTo(contentBefore);
        try (InputStream stream = rotatedCipher.open(file)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("rotating");
        }
    }

    @Test
    void shouldLeaveFileWrappedWithActiveKeyUntouched() throws Exception {
        // given
        AssetFileCipher cipher = new AssetFileCipher(TestEncryption.envelopeCipher());
        Path file = root.resolve("asset.bin");
        try (AssetFileCipher.Appender appender = cipher.create(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            appender.write(new ByteArrayInputStream("stable".getBytes(StandardCharsets.UTF_8)), 100);
        }
        byte[] before = Files.readAllBytes(file);

        // when
        cipher.secure(file);

        // then
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
}
