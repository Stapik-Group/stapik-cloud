package pl.stapik.cloud.asset;

import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.asset.dto.StartAssetUpload;

import java.io.InputStream;
import java.util.UUID;

public interface AssetUploadService {
    AssetUploadResult start(UUID extensionId, StartAssetUpload command);
    AssetUploadResult get(UUID extensionId, UUID uploadId);
    AssetUploadResult append(UUID extensionId, UUID uploadId, long offset, InputStream chunk);
    void abort(UUID extensionId, UUID uploadId);
    int deleteExpired();
}
