package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;
import pl.stapik.cloud.asset.impl.AssetServiceImpl;
import pl.stapik.cloud.internal.api.AssetsApiDelegate;
import pl.stapik.cloud.internal.data.AssetListResponse;
import pl.stapik.cloud.internal.data.AssetMetadataResponse;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssetDelegate implements AssetsApiDelegate {

    private final AssetServiceImpl assetServiceImpl;
    private final AssetMapper assetMapper;

    @Override
    public ResponseEntity<AssetListResponse> listAssets(String slotKey) {
        List<AssetMetadataResponse> assets = assetServiceImpl.list(AssetIdentifier.of(currentExtensionId(), slotKey)).stream()
                .map(assetMapper::toResponse)
                .toList();

        return ResponseEntity.ok(new AssetListResponse().assets(assets));
    }

    @Override
    public ResponseEntity<AssetMetadataResponse> uploadAsset(String slotKey, String filename, MultipartFile file) {
        AssetData assetData = assetServiceImpl.upload(AssetIdentifier.of(currentExtensionId(), slotKey, filename), file);
        return ResponseEntity.ok(assetMapper.toResponse(assetData));
    }

    @Override
    public ResponseEntity<Resource> downloadAsset(String slotKey, String filename) {
        Resource resource = assetServiceImpl.download(AssetIdentifier.of(currentExtensionId(), slotKey, filename));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Override
    public ResponseEntity<Void> deleteAsset(String slotKey, String filename) {
        assetServiceImpl.delete(AssetIdentifier.of(currentExtensionId(), slotKey, filename));
        return ResponseEntity.noContent().build();
    }

    private UUID currentExtensionId() {
        return Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(ApiKeyPrincipal.class::cast)
                .map(ApiKeyPrincipal::extensionId)
                .orElseThrow();
    }
}