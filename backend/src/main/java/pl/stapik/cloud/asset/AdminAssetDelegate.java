package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.admin.api.AdminAssetsApiDelegate;
import pl.stapik.cloud.admin.data.AdminAssetListResponse;
import pl.stapik.cloud.admin.data.AdminAssetResponse;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;
import pl.stapik.cloud.audit.Auditing;
import pl.stapik.cloud.audit.data.AuditAction;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminAssetDelegate implements AdminAssetsApiDelegate {

    private final AssetService assetService;
    private final AdminAssetMapper adminAssetMapper;

    @Override
    public ResponseEntity<AdminAssetListResponse> listAdminAssets(UUID extensionId, String slotKey) {
        List<AdminAssetResponse> assets = assetService.list(AssetIdentifier.of(extensionId, slotKey)).stream()
                .sorted(Comparator.comparing(AssetData::getFilename))
                .map(adminAssetMapper::toResponse)
                .toList();

        return ResponseEntity.ok(new AdminAssetListResponse().assets(assets));
    }

    @Override
    public ResponseEntity<Resource> downloadAdminAsset(UUID extensionId, String slotKey, String filename, Boolean inline) {
        AssetIdentifier identifier = AssetIdentifier.of(extensionId, slotKey, filename);
        AssetData metadata = assetService.getMetadata(identifier);
        boolean servedInline = Boolean.TRUE.equals(inline) && AssetPreviewPolicy.isPreviewable(metadata.getMimeType());

        ContentDisposition disposition = (servedInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(metadata.getFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(servedInline ? MediaType.parseMediaType(metadata.getMimeType()) : MediaType.APPLICATION_OCTET_STREAM)
                .body(assetService.download(identifier));
    }

    @Override
    @Auditing(action = AuditAction.ASSET_UPLOADED, extensionId = "#extensionId",
            details = "'File ' + #slotKey + '/' + #filename + ' uploaded'")
    public ResponseEntity<AdminAssetResponse> uploadAdminAsset(UUID extensionId, String slotKey, String filename, MultipartFile file) {
        AssetData uploaded = assetService.upload(AssetIdentifier.of(extensionId, slotKey, filename), file, null);
        return ResponseEntity.ok(adminAssetMapper.toResponse(uploaded));
    }

    @Override
    @Auditing(action = AuditAction.ASSET_DELETED, extensionId = "#extensionId",
            details = "'File ' + #slotKey + '/' + #filename + ' deleted'")
    public ResponseEntity<Void> deleteAdminAsset(UUID extensionId, String slotKey, String filename) {
        assetService.delete(AssetIdentifier.of(extensionId, slotKey, filename));
        return ResponseEntity.noContent().build();
    }
}
