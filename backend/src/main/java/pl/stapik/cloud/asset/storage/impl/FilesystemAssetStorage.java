package pl.stapik.cloud.asset.storage.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.storage.AssetStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class FilesystemAssetStorage implements AssetStorage {

    private final Path rootPath;

    public FilesystemAssetStorage(@Value("${stapik-cloud.asset-storage.root-path}") String rootPath) {
        this.rootPath = Path.of(rootPath);
    }

    @Override
    public StoredAsset store(UUID extensionId, UUID slotId, String filename, MultipartFile file) throws IOException {
        Path targetDir = rootPath.resolve(extensionId.toString()).resolve(slotId.toString());
        Files.createDirectories(targetDir);

        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new IOException("Invalid filename (path traversal attempt): " + filename);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

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
}