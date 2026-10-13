package pl.stapik.cloud.asset.dto;

import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.data.AssetUploadData;

public record AssetUploadResult(AssetUploadData upload, AssetData asset) {

    public static AssetUploadResult inProgress(AssetUploadData upload) {
        return new AssetUploadResult(upload, null);
    }

    public static AssetUploadResult completed(AssetUploadData upload, AssetData asset) {
        return new AssetUploadResult(upload, asset);
    }
}
