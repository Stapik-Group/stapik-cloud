package pl.stapik.cloud.asset.storage.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.AssetChecksumMismatchException;
import pl.stapik.cloud.asset.storage.AssetStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class FilesystemAssetStorage implements AssetStorage {

    private static final String UPLOADS_DIRECTORY = ".uploads";
    private static final String PART_FILE_SUFFIX = ".part";
    private static final int BUFFER_SIZE = 64 * 1024;

    private record Fingerprint(String checksumSha256, long sizeBytes) { }

    private final Path rootPath;
    private final AssetFileCipher cipher;

    public FilesystemAssetStorage(@Value("${stapik-cloud.asset-storage.root-path}") String rootPath, AssetFileCipher cipher) {
        this.rootPath = Path.of(rootPath);
        this.cipher = cipher;
    }

    @Override
    public StagedAsset stage(UUID extensionId, UUID slotId, String filename, MultipartFile file, String expectedChecksumSha256) throws IOException {
        Path targetFile = resolveTargetFile(extensionId, slotId, filename);
        Path stagingFile = rootPath.resolve(UPLOADS_DIRECTORY).resolve(UUID.randomUUID() + PART_FILE_SUFFIX);
        Files.createDirectories(stagingFile.getParent());

        MessageDigest digest = newSha256Digest();

        try {
            long sizeBytes;
            try (DigestInputStream digestStream = new DigestInputStream(file.getInputStream(), digest);
                 AssetFileCipher.Appender appender = cipher.create(
                         stagingFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                sizeBytes = appender.write(digestStream, Long.MAX_VALUE);
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            requireMatchingChecksum(expectedChecksumSha256, checksum);

            return stagedAsset(stagingFile, targetFile, checksum, sizeBytes);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(stagingFile);
            throw e;
        }
    }

    @Override
    public StagedAsset stageUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename, String expectedChecksumSha256) throws IOException {
        Path partFile = resolvePartFile(uploadId);
        Path targetFile = resolveTargetFile(extensionId, slotId, filename);

        Fingerprint fingerprint = fingerprintOf(partFile);
        requireMatchingChecksum(expectedChecksumSha256, fingerprint.checksumSha256());

        return stagedAsset(partFile, targetFile, fingerprint.checksumSha256(), fingerprint.sizeBytes());
    }

    @Override
    public void promote(StagedAsset staged) throws IOException {
        Path stagingFile = resolveInsideRoot(staged.stagingPath());
        Path targetFile = resolveInsideRoot(staged.stored().storagePath());

        Files.createDirectories(targetFile.getParent());
        Files.move(stagingFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void discardStaged(StagedAsset staged) throws IOException {
        Files.deleteIfExists(resolveInsideRoot(staged.stagingPath()));
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath) || !Files.exists(file)) {
            throw new IOException("Asset file not found: " + storagePath);
        }
        return cipher.isEncrypted(file) ? new EncryptedAssetResource(file, cipher) : new FileSystemResource(file);
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath)) {
            throw new IOException("Invalid storage path: " + storagePath);
        }
        Files.deleteIfExists(file);
    }

    @Override
    public void beginUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename) throws IOException {
        resolveTargetFile(extensionId, slotId, filename);

        Path partFile = resolvePartFile(uploadId);
        Files.createDirectories(partFile.getParent());
        cipher.create(partFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close();
    }

    @Override
    public long appendChunk(UUID uploadId, long offset, InputStream chunk, long maxBytes) throws IOException {
        try (AssetFileCipher.Appender appender = cipher.resume(resolvePartFile(uploadId), offset)) {
            return appender.write(chunk, maxBytes);
        }
    }

    @Override
    public void discardUpload(UUID uploadId) throws IOException {
        Files.deleteIfExists(resolvePartFile(uploadId));
    }

    @Override
    public void secure(String storagePath) throws IOException {
        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath) || !Files.exists(file)) {
            throw new IOException("Asset file not found: " + storagePath);
        }
        cipher.secure(file);
    }

    private void requireMatchingChecksum(String expectedChecksumSha256, String actualChecksumSha256) {
        if (expectedChecksumSha256 != null && !expectedChecksumSha256.equalsIgnoreCase(actualChecksumSha256)) {
            throw new AssetChecksumMismatchException(expectedChecksumSha256, actualChecksumSha256);
        }
    }

    private Path resolveTargetFile(UUID extensionId, UUID slotId, String filename) throws IOException {
        Path targetDir = rootPath.resolve(extensionId.toString()).resolve(slotId.toString());
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new IOException("Invalid filename (path traversal attempt): " + filename);
        }
        return targetFile;
    }

    private Path resolveInsideRoot(String storagePath) throws IOException {
        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath)) {
            throw new IOException("Invalid storage path: " + storagePath);
        }
        return file;
    }

    private StagedAsset stagedAsset(Path stagingFile, Path targetFile, String checksum, long sizeBytes) {
        StoredAsset stored = new StoredAsset(rootPath.relativize(targetFile).toString(), checksum, sizeBytes);
        return new StagedAsset(stored, rootPath.relativize(stagingFile).toString());
    }

    private Path resolvePartFile(UUID uploadId) {
        return rootPath.resolve(UPLOADS_DIRECTORY).resolve(uploadId + PART_FILE_SUFFIX);
    }

    private Fingerprint fingerprintOf(Path encryptedFile) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        long sizeBytes = 0;
        int read;

        try (InputStream stream = cipher.open(encryptedFile)) {
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                sizeBytes += read;
            }
        }

        return new Fingerprint(HexFormat.of().formatHex(digest.digest()), sizeBytes);
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
