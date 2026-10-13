package pl.stapik.cloud.asset.dto;

public record StartAssetUpload(String slotKey, String filename, long sizeBytes, String mimeType, String checksumSha256) {
}
