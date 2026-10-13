package pl.stapik.cloud.asset;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;
import pl.stapik.cloud.asset.storage.AssetStorage;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.util.List;

public interface AssetService {
    List<AssetData> list(AssetIdentifier identifier);
    AssetData getMetadata(AssetIdentifier identifier);
    AssetData upload(AssetIdentifier identifier, MultipartFile file, String expectedChecksumSha256);
    AssetData saveStoredAsset(DocumentSlotData slot, String filename, String mimeType, AssetStorage.StoredAsset stored);
    Resource download(AssetIdentifier identifier);
    void delete(AssetIdentifier identifier);
}
