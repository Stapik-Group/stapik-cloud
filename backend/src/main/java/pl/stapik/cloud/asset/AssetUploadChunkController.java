package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.internal.data.AssetUploadResponse;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetUploadChunkController {

    private final AssetUploadService assetUploadService;
    private final AssetUploadMapper assetUploadMapper;

    @PatchMapping(
            path = "${stapik-cloud.asset-upload.base-path}/{uploadId}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AssetUploadResponse> appendChunk(@PathVariable UUID uploadId,
                                                           @RequestHeader("Upload-Offset") long uploadOffset,
                                                           InputStream chunk) {
        AssetUploadResult result = assetUploadService.append(CurrentExtension.id(), uploadId, uploadOffset, chunk);
        return ResponseEntity.ok(assetUploadMapper.toResponse(result));
    }
}
