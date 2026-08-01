package pl.stapik.cloud.asset.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface AssetStorage {

    record StoredAsset(String storagePath, String checksumSha256, long sizeBytes) { }

    StoredAsset store(UUID extensionId, UUID slotId, String filename, MultipartFile file) throws IOException;

    Resource load(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}