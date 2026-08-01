package pl.stapik.cloud.asset.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.AssetRepository;
import pl.stapik.cloud.asset.AssetService;
import pl.stapik.cloud.asset.AssetTooLargeException;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;
import pl.stapik.cloud.asset.storage.AssetStorage;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final DocumentSlotRepository documentSlotRepository;
    private final AssetStorage assetStorage;

    @Override
    public List<AssetData> list(AssetIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier.getExtensionId(), identifier.getSlotKey());
        return assetRepository.findByDocumentSlotId(slot.getId());
    }

    @Override
    public AssetData upload(AssetIdentifier identifier, MultipartFile file) {
        DocumentSlotData slot = requireSlot(identifier.getExtensionId(), identifier.getSlotKey());

        if (file.getSize() > slot.getMaxSizeBytes()) {
            throw new AssetTooLargeException(slot.getMaxSizeBytes(), file.getSize());
        }

        try {
            AssetStorage.StoredAsset stored = assetStorage.store(identifier.getExtensionId(), slot.getId(), identifier.getFilename(), file);

            AssetData assetData = assetRepository.findByDocumentSlotIdAndFilename(slot.getId(), identifier.getFilename())
                    .orElseGet(() -> AssetData.builder()
                            .documentSlotId(slot.getId())
                            .filename(identifier.getFilename())
                            .createdAt(Instant.now())
                            .build());

            assetData.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            assetData.setSizeBytes(stored.sizeBytes());
            assetData.setStoragePath(stored.storagePath());
            assetData.setChecksumSha256(stored.checksumSha256());
            assetData.setUpdatedAt(Instant.now());

            return assetRepository.save(assetData);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store asset: " + identifier.getFilename(), e);
        }
    }

    @Override
    public Resource download(AssetIdentifier identifier) {
        AssetData assetData = requireAsset(identifier);
        try {
            return assetStorage.load(assetData.getStoragePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load asset: " + identifier.getFilename(), e);
        }
    }

    @Override
    public void delete(AssetIdentifier identifier) {
        AssetData assetData = requireAsset(identifier);
        try {
            assetStorage.delete(assetData.getStoragePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete asset file: " + identifier.getFilename(), e);
        }
        assetRepository.delete(assetData);
    }

    private AssetData requireAsset(AssetIdentifier identifier) {
        DocumentSlotData slot = requireSlot(identifier.getExtensionId(), identifier.getSlotKey());
        return assetRepository.findByDocumentSlotIdAndFilename(slot.getId(), identifier.getFilename())
                .orElseThrow(() -> new NoSuchElementException("Asset not found: " + identifier.getFilename()));
    }

    private DocumentSlotData requireSlot(UUID extensionId, String slotKey) {
        return documentSlotRepository.findByExtensionIdAndSlotKey(extensionId, slotKey)
                .orElseThrow(() -> new NoSuchElementException("Document slot not found: " + slotKey));
    }
}