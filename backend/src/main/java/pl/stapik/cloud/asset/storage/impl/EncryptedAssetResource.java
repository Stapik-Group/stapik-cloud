package pl.stapik.cloud.asset.storage.impl;

import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class EncryptedAssetResource extends AbstractResource {

    private final Path file;
    private final AssetFileCipher cipher;

    EncryptedAssetResource(Path file, AssetFileCipher cipher) {
        this.file = file;
        this.cipher = cipher;
    }

    @Override
    public String getDescription() {
        return "encrypted asset [" + file + "]";
    }

    @Override
    public String getFilename() {
        return file.getFileName().toString();
    }

    @Override
    public boolean exists() {
        return Files.exists(file);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return cipher.open(file);
    }

    @Override
    public long contentLength() throws IOException {
        return cipher.plaintextSize(file);
    }

    @Override
    public long lastModified() throws IOException {
        return Files.getLastModifiedTime(file).toMillis();
    }
}
