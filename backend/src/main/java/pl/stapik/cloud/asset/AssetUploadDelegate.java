package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.internal.api.AssetUploadsApiDelegate;
import pl.stapik.cloud.internal.data.AssetUploadResponse;
import pl.stapik.cloud.internal.data.CreateAssetUploadRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssetUploadDelegate implements AssetUploadsApiDelegate {

    private final AssetUploadService assetUploadService;
    private final AssetUploadMapper assetUploadMapper;

    @Override
    public ResponseEntity<AssetUploadResponse> createAssetUpload(CreateAssetUploadRequest createAssetUploadRequest) {
        AssetUploadResult result = assetUploadService.start(CurrentExtension.id(), assetUploadMapper.toCommand(createAssetUploadRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(assetUploadMapper.toResponse(result));
    }

    @Override
    public ResponseEntity<AssetUploadResponse> getAssetUpload(UUID uploadId) {
        AssetUploadResult result = assetUploadService.get(CurrentExtension.id(), uploadId);
        return ResponseEntity.ok(assetUploadMapper.toResponse(result));
    }

    @Override
    public ResponseEntity<Void> abortAssetUpload(UUID uploadId) {
        assetUploadService.abort(CurrentExtension.id(), uploadId);
        return ResponseEntity.noContent().build();
    }
}
