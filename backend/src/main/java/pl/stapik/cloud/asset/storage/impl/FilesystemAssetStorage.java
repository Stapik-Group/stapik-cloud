package pl.stapik.cloud.asset.storage.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.AssetChecksumMismatchException;
import pl.stapik.cloud.asset.AssetChunkTooLargeException;
import pl.stapik.cloud.asset.storage.AssetStorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
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

    private final Path rootPath;

    public FilesystemAssetStorage(@Value("${stapik-cloud.asset-storage.root-path}") String rootPath) {
        this.rootPath = Path.of(rootPath);
    }

    @Override
    public StoredAsset store(UUID extensionId, UUID slotId, String filename, MultipartFile file) throws IOException {
        Path targetFile = resolveTargetFile(extensionId, slotId, filename);
        Files.createDirectories(targetFile.getParent());

        MessageDigest digest = newSha256Digest();

        long sizeBytes;
        try (DigestInputStream digestStream = new DigestInputStream(file.getInputStream(), digest)) {
            sizeBytes = Files.copy(digestStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String checksum = HexFormat.of().formatHex(digest.digest());
        String relativePath = rootPath.relativize(targetFile).toString();

        return new StoredAsset(relativePath, checksum, sizeBytes);
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath) || !Files.exists(file)) {
            throw new IOException("Asset file not found: " + storagePath);
        }
        return new FileSystemResource(file);
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
        Files.createFile(partFile);
    }

    @Override
    public long appendChunk(UUID uploadId, long offset, InputStream chunk, long maxBytes) throws IOException {
        try (FileChannel channel = FileChannel.open(resolvePartFile(uploadId), StandardOpenOption.WRITE)) {
            if (channel.size() < offset) {
                throw new IOException("Upload file is shorter than the committed offset: " + uploadId);
            }
            channel.truncate(offset);
            channel.position(offset);
            return copyBounded(chunk, Channels.newOutputStream(channel), maxBytes);
        }
    }

    @Override
    public StoredAsset commitUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename, String expectedChecksumSha256) throws IOException {
        Path partFile = resolvePartFile(uploadId);
        Path targetFile = resolveTargetFile(extensionId, slotId, filename);

        String checksum = checksumOf(partFile);
        if (expectedChecksumSha256 != null && !expectedChecksumSha256.equalsIgnoreCase(checksum)) {
            throw new AssetChecksumMismatchException(expectedChecksumSha256, checksum);
        }

        Files.createDirectories(targetFile.getParent());
        Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        return new StoredAsset(rootPath.relativize(targetFile).toString(), checksum, Files.size(targetFile));
    }

    @Override
    public void discardUpload(UUID uploadId) throws IOException {
        Files.deleteIfExists(resolvePartFile(uploadId));
    }

    private Path resolveTargetFile(UUID extensionId, UUID slotId, String filename) throws IOException {
        Path targetDir = rootPath.resolve(extensionId.toString()).resolve(slotId.toString());
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new IOException("Invalid filename (path traversal attempt): " + filename);
        }
        return targetFile;
    }

    private Path resolvePartFile(UUID uploadId) {
        return rootPath.resolve(UPLOADS_DIRECTORY).resolve(uploadId + PART_FILE_SUFFIX);
    }

    private long copyBounded(InputStream source, OutputStream target, long maxBytes) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int read;

        while ((read = source.read(buffer)) != -1) {
            copied += read;
            if (copied > maxBytes) {
                throw new AssetChunkTooLargeException(maxBytes);
            }
            target.write(buffer, 0, read);
        }

        return copied;
    }

    private String checksumOf(Path file) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;

        try (InputStream stream = Files.newInputStream(file)) {
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
