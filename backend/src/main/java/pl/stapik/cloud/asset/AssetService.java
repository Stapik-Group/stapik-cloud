package pl.stapik.cloud.asset;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;

import java.util.List;

public interface AssetService {
    List<AssetData> list(AssetIdentifier identifier);
    AssetData upload(AssetIdentifier identifier, MultipartFile file);
    Resource download(AssetIdentifier identifier);
    void delete(AssetIdentifier identifier);
}
