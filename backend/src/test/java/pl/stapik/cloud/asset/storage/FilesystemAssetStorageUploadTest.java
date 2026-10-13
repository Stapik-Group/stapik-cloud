package pl.stapik.cloud.asset.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.stapik.cloud.asset.AssetChecksumMismatchException;
import pl.stapik.cloud.asset.AssetChunkTooLargeException;
import pl.stapik.cloud.asset.storage.impl.FilesystemAssetStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        storage = new FilesystemAssetStorage(root.toString());
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
        assertThat(Files.readString(root.resolve(stored.storagePath()))).isEqualTo("hello world");
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
        assertThat(Files.readString(root.resolve(stored.storagePath()))).isEqualTo("abcdef");
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

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
