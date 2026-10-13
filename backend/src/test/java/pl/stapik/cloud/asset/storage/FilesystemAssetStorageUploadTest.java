package pl.stapik.cloud.asset.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import pl.stapik.cloud.asset.AssetChecksumMismatchException;
import pl.stapik.cloud.asset.AssetChunkTooLargeException;
import pl.stapik.cloud.asset.storage.impl.AssetFileCipher;
import pl.stapik.cloud.asset.storage.impl.FilesystemAssetStorage;
import pl.stapik.cloud.common.crypto.TestEncryption;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemAssetStorageUploadTest {

    private static final UUID EXTENSION_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final String FILENAME = "video.bin";

    @TempDir
    Path root;

    private FilesystemAssetStorage storage;
    private UUID uploadId;

    @BeforeEach
    void setUp() {
        storage = new FilesystemAssetStorage(root.toString(), new AssetFileCipher(TestEncryption.envelopeCipher()));
        uploadId = UUID.randomUUID();
    }

    @Test
    void shouldAssembleChunksIntoAsset() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when
        long first = storage.appendChunk(uploadId, 0, stream("hello "), 100);
        long second = storage.appendChunk(uploadId, first, stream("world"), 100);
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);

        // then
        assertThat(first + second).isEqualTo(11);
        assertThat(stored.sizeBytes()).isEqualTo(11);
        assertThat(stored.checksumSha256()).isEqualTo(sha256("hello world"));
        assertThat(read(stored)).isEqualTo("hello world");
        assertThat(root.resolve(".uploads").resolve(uploadId + ".part")).doesNotExist();
    }

    @Test
    void shouldOverwriteUncommittedBytesWhenResumingAtCommittedOffset() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("abc"), 100);
        storage.appendChunk(uploadId, 3, stream("garbage"), 100);

        // when
        storage.appendChunk(uploadId, 3, stream("def"), 100);
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);

        // then
        assertThat(read(stored)).isEqualTo("abcdef");
    }

    @Test
    void shouldKeepPlaintextOutOfPartAndStoredFiles() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("very secret content"), 100);

        // when
        byte[] partBytes = Files.readAllBytes(root.resolve(".uploads").resolve(uploadId + ".part"));
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);
        byte[] storedBytes = Files.readAllBytes(root.resolve(stored.storagePath()));

        // then
        assertThat(new String(partBytes, StandardCharsets.ISO_8859_1)).doesNotContain("very secret content");
        assertThat(new String(storedBytes, StandardCharsets.ISO_8859_1)).doesNotContain("very secret content");
        assertThat(storage.load(stored.storagePath()).contentLength()).isEqualTo(19);
    }

    @Test
    void shouldResumeAcrossFrameBoundaries() throws Exception {
        // given
        byte[] content = new byte[200_000];
        new Random(7).nextBytes(content);
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when
        long first = storage.appendChunk(uploadId, 0, new ByteArrayInputStream(content, 0, 70_000), 1_000_000);
        storage.appendChunk(uploadId, first, new ByteArrayInputStream(content, 70_000, 130_000), 1_000_000);
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);

        // then
        assertThat(stored.sizeBytes()).isEqualTo(200_000);
        assertThat(storage.load(stored.storagePath()).getInputStream().readAllBytes()).isEqualTo(content);
        assertThat(storage.load(stored.storagePath()).contentLength()).isEqualTo(200_000);
    }

    @Test
    void shouldStoreMultipartFileEncrypted() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile("file", FILENAME, "text/plain", "plain text".getBytes(StandardCharsets.UTF_8));

        // when
        AssetStorage.StoredAsset stored = storage.store(EXTENSION_ID, SLOT_ID, FILENAME, file, null);

        // then
        assertThat(stored.sizeBytes()).isEqualTo(10);
        assertThat(stored.checksumSha256()).isEqualTo(sha256("plain text"));
        assertThat(new String(Files.readAllBytes(root.resolve(stored.storagePath())), StandardCharsets.ISO_8859_1)).doesNotContain("plain text");
        assertThat(read(stored)).isEqualTo("plain text");
    }

    @Test
    void shouldStoreMultipartFileWhenExpectedChecksumMatches() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile("file", FILENAME, "text/plain", "plain text".getBytes(StandardCharsets.UTF_8));

        // when
        AssetStorage.StoredAsset stored = storage.store(EXTENSION_ID, SLOT_ID, FILENAME, file, sha256("plain text").toUpperCase());

        // then
        assertThat(read(stored)).isEqualTo("plain text");
    }

    @Test
    void shouldKeepPreviousFileWhenMultipartChecksumDoesNotMatch() throws Exception {
        // given
        AssetStorage.StoredAsset previous = storage.store(EXTENSION_ID, SLOT_ID, FILENAME,
                new MockMultipartFile("file", FILENAME, "text/plain", "old".getBytes(StandardCharsets.UTF_8)), null);
        MockMultipartFile replacement = new MockMultipartFile("file", FILENAME, "text/plain", "new".getBytes(StandardCharsets.UTF_8));

        // when / then
        assertThatThrownBy(() -> storage.store(EXTENSION_ID, SLOT_ID, FILENAME, replacement, sha256("other")))
                .isInstanceOf(AssetChecksumMismatchException.class);
        assertThat(read(previous)).isEqualTo("old");
        assertThat(root.resolve(".uploads")).isEmptyDirectory();
    }

    @Test
    void shouldStoreEmptyAsset() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);

        // then
        assertThat(stored.sizeBytes()).isZero();
        assertThat(storage.load(stored.storagePath()).contentLength()).isZero();
        assertThat(read(stored)).isEmpty();
    }

    @Test
    void shouldServeLegacyPlaintextFileAsIs() throws Exception {
        // given
        Path legacy = root.resolve("legacy.txt");
        Files.writeString(legacy, "legacy content");

        // when / then
        assertThat(new String(storage.load("legacy.txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("legacy content");
    }

    @Test
    void shouldEncryptLegacyPlaintextFileInPlace() throws Exception {
        // given
        Files.writeString(root.resolve("legacy.txt"), "legacy content");

        // when
        storage.secure("legacy.txt");

        // then
        assertThat(Files.readString(root.resolve("legacy.txt"), StandardCharsets.ISO_8859_1)).doesNotContain("legacy content");
        assertThat(new String(storage.load("legacy.txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("legacy content");
        assertThat(root.resolve("legacy.txt.encrypting")).doesNotExist();
    }

    @Test
    void shouldFailToSecureMissingFile() {
        assertThatThrownBy(() -> storage.secure("missing.bin")).isInstanceOf(IOException.class);
    }

    @Test
    void shouldDetectModifiedStoredFile() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("content to protect"), 100);
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);
        Path file = root.resolve(stored.storagePath());
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);

        // when / then
        assertThatThrownBy(() -> storage.load(stored.storagePath()).getInputStream().readAllBytes())
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldDetectTruncatedStoredFile() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("content to protect"), 100);
        AssetStorage.StoredAsset stored = storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, null);
        Path file = root.resolve(stored.storagePath());
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(bytes, bytes.length - 3));

        // when / then
        assertThatThrownBy(() -> storage.load(stored.storagePath()).getInputStream().readAllBytes())
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectChunkLargerThanAllowed() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when / then
        ByteArrayInputStream chunk = stream("too long");
        assertThatThrownBy(() -> storage.appendChunk(uploadId, 0, chunk, 3))
                .isInstanceOf(AssetChunkTooLargeException.class);
    }

    @Test
    void shouldRejectChunkBeyondEndOfReceivedData() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when / then
        ByteArrayInputStream chunk = stream("x");
        assertThatThrownBy(() -> storage.appendChunk(uploadId, 5, chunk, 10))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectCommitWhenChecksumDoesNotMatch() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("content"), 100);

        // when / then
        String wrongChecksum = "0".repeat(64);
        assertThatThrownBy(() -> storage.commitUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME, wrongChecksum))
                .isInstanceOf(AssetChecksumMismatchException.class);
    }

    @Test
    void shouldAcceptUpperCaseExpectedChecksum() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);
        storage.appendChunk(uploadId, 0, stream("content"), 100);

        // when
        AssetStorage.StoredAsset stored = storage.commitUpload(
                uploadId, EXTENSION_ID, SLOT_ID, FILENAME, sha256("content").toUpperCase());

        // then
        assertThat(stored.checksumSha256()).isEqualTo(sha256("content"));
    }

    @Test
    void shouldRejectPathTraversalFilenameWhenBeginningUpload() {
        // when / then
        assertThatThrownBy(() -> storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, "../../escape.bin"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldDeletePartFileOnDiscard() throws Exception {
        // given
        storage.beginUpload(uploadId, EXTENSION_ID, SLOT_ID, FILENAME);

        // when
        storage.discardUpload(uploadId);

        // then
        assertThat(root.resolve(".uploads").resolve(uploadId + ".part")).doesNotExist();
    }

    private String read(AssetStorage.StoredAsset stored) throws IOException {
        return new String(storage.load(stored.storagePath()).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
