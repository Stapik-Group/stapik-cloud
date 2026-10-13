package pl.stapik.cloud.asset.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface AssetStorage {
    record StoredAsset(String storagePath, String checksumSha256, long sizeBytes) { }
    record StagedAsset(StoredAsset stored, String stagingPath) { }
    StagedAsset stage(UUID extensionId, UUID slotId, String filename, MultipartFile file, String expectedChecksumSha256) throws IOException;
    StagedAsset stageUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename, String expectedChecksumSha256) throws IOException;
    void promote(StagedAsset staged) throws IOException;
    void discardStaged(StagedAsset staged) throws IOException;
    default StoredAsset store(UUID extensionId, UUID slotId, String filename, MultipartFile file, String expectedChecksumSha256) throws IOException {
        StagedAsset staged = stage(extensionId, slotId, filename, file, expectedChecksumSha256);
        promote(staged);
        return staged.stored();
    }
    Resource load(String storagePath) throws IOException;
    void delete(String storagePath) throws IOException;
    void beginUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename) throws IOException;
    long appendChunk(UUID uploadId, long offset, InputStream chunk, long maxBytes) throws IOException;
    default StoredAsset commitUpload(UUID uploadId, UUID extensionId, UUID slotId, String filename, String expectedChecksumSha256) throws IOException {
        StagedAsset staged = stageUpload(uploadId, extensionId, slotId, filename, expectedChecksumSha256);
        promote(staged);
        return staged.stored();
    }
    void discardUpload(UUID uploadId) throws IOException;
    void secure(String storagePath) throws IOException;
}
