package pl.stapik.cloud.asset.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface AssetStorage {
    record StoredAsset(String storagePath, String checksumSha256, long sizeBytes) { }
    StoredAsset store(UUID extensionId, UUID slotId, String filename, MultipartFile file, String expectedChecksumSha256) throws IOException;
    Resource load(String storagePath) throws IOException;
    void delete(String storagePath) throws IOException;
    void beginUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename) throws IOException;
    long appendChunk(UUID uploadId, long offset, InputStream chunk, long maxBytes) throws IOException;
    StoredAsset commitUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename, String expectedChecksumSha256) throws IOException;
    void discardUpload(UUID uploadId) throws IOException;
    void secure(String storagePath) throws IOException;
}
