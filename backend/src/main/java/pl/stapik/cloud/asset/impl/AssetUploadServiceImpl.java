package pl.stapik.cloud.asset.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.asset.AssetChecksumMismatchException;
import pl.stapik.cloud.asset.AssetMimeTypePolicy;
import pl.stapik.cloud.asset.AssetService;
import pl.stapik.cloud.asset.AssetTooLargeException;
import pl.stapik.cloud.asset.AssetUploadOffsetMismatchException;
import pl.stapik.cloud.asset.AssetUploadProperties;
import pl.stapik.cloud.asset.AssetUploadRepository;
import pl.stapik.cloud.asset.AssetUploadService;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.data.AssetUploadData;
import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.asset.dto.StartAssetUpload;
import pl.stapik.cloud.asset.storage.AssetStorage;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetUploadServiceImpl implements AssetUploadService {

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final AssetUploadRepository assetUploadRepository;
    private final DocumentSlotRepository documentSlotRepository;
    private final AssetService assetService;
    private final AssetStorage assetStorage;
    private final AssetMimeTypePolicy assetMimeTypePolicy;
    private final AssetUploadProperties properties;
    private final Clock clock;

    private record UploadContext(AssetUploadData upload, DocumentSlotData slot) { }

    @Transactional
    @Override
    public AssetUploadResult start(UUID extensionId, StartAssetUpload command) {
        DocumentSlotData slot = documentSlotRepository.findByExtensionIdAndSlotKey(extensionId, command.slotKey())
                .orElseThrow(() -> new NoSuchElementException("Document slot not found: " + command.slotKey()));

        if (command.sizeBytes() > slot.getMaxSizeBytes()) {
            throw new AssetTooLargeException(slot.getMaxSizeBytes(), command.sizeBytes());
        }

        Instant now = clock.instant();
        AssetUploadData upload = assetUploadRepository.save(AssetUploadData.builder()
                .documentSlotId(slot.getId())
                .filename(command.filename())
                .mimeType(command.mimeType() != null ? command.mimeType() : DEFAULT_MIME_TYPE)
                .sizeBytes(command.sizeBytes())
                .expectedChecksumSha256(normalizeChecksum(command.checksumSha256()))
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plus(properties.sessionTtl()))
                .build());

        try {
            assetStorage.beginUpload(upload.getId(), extensionId, slot.getId(), upload.getFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to begin upload: " + command.filename(), e);
        }

        return AssetUploadResult.inProgress(upload);
    }

    @Override
    public AssetUploadResult get(UUID extensionId, UUID uploadId) {
        UploadContext context = requireActiveUpload(extensionId, assetUploadRepository.findById(uploadId), uploadId);
        return AssetUploadResult.inProgress(context.upload());
    }

    @Transactional(noRollbackFor = AssetChecksumMismatchException.class)
    @Override
    public AssetUploadResult append(UUID extensionId, UUID uploadId, long offset, InputStream chunk) {
        UploadContext context = requireActiveUpload(extensionId, assetUploadRepository.findForUpdateById(uploadId), uploadId);
        AssetUploadData upload = context.upload();

        if (offset != upload.getReceivedBytes()) {
            throw new AssetUploadOffsetMismatchException(upload.getReceivedBytes(), offset);
        }

        InputStream content = offset == 0 ? resolveMimeTypeFromFirstChunk(context, chunk) : chunk;

        long remainingBytes = upload.getSizeBytes() - upload.getReceivedBytes();
        long maxChunkBytes = Math.min(remainingBytes, properties.maxChunkSize().toBytes());

        try {
            long writtenBytes = assetStorage.appendChunk(uploadId, offset, content, maxChunkBytes);
            upload.setReceivedBytes(offset + writtenBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append chunk to upload: " + uploadId, e);
        }

        Instant now = clock.instant();
        upload.setUpdatedAt(now);
        upload.setExpiresAt(now.plus(properties.sessionTtl()));
        assetUploadRepository.save(upload);

        if (upload.getReceivedBytes() < upload.getSizeBytes()) {
            return AssetUploadResult.inProgress(upload);
        }

        return AssetUploadResult.completed(upload, complete(context));
    }

    private InputStream resolveMimeTypeFromFirstChunk(UploadContext context, InputStream chunk) {
        AssetUploadData upload = context.upload();
        InputStream content = new BufferedInputStream(chunk);
        try {
            upload.setMimeType(assetMimeTypePolicy.resolve(context.slot(), upload.getFilename(), content));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect upload: " + upload.getId(), e);
        }
        return content;
    }

    @Transactional
    @Override
    public void abort(UUID extensionId, UUID uploadId) {
        UploadContext context = requireActiveUpload(extensionId, assetUploadRepository.findById(uploadId), uploadId);
        discard(context.upload());
    }

    @Transactional
    @Override
    public int deleteExpired() {
        List<AssetUploadData> expired = assetUploadRepository.findByExpiresAtBefore(clock.instant());
        expired.forEach(this::discard);
        return expired.size();
    }

    private AssetData complete(UploadContext context) {
        AssetUploadData upload = context.upload();
        DocumentSlotData slot = context.slot();

        try {
            AssetStorage.StoredAsset stored = assetStorage.commitUpload(
                    upload.getId(), slot.getExtensionId(), slot.getId(), upload.getFilename(), upload.getExpectedChecksumSha256());
            AssetData asset = assetService.saveStoredAsset(slot, upload.getFilename(), upload.getMimeType(), stored);
            assetUploadRepository.delete(upload);
            return asset;
        } catch (AssetChecksumMismatchException e) {
            discard(upload);
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to complete upload: " + upload.getId(), e);
        }
    }

    private void discard(AssetUploadData upload) {
        try {
            assetStorage.discardUpload(upload.getId());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to discard upload: " + upload.getId(), e);
        }
        assetUploadRepository.delete(upload);
    }

    private UploadContext requireActiveUpload(UUID extensionId, Optional<AssetUploadData> found, UUID uploadId) {
        AssetUploadData upload = found
                .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> uploadNotFound(uploadId));

        DocumentSlotData slot = documentSlotRepository.findById(upload.getDocumentSlotId())
                .filter(candidate -> candidate.getExtensionId().equals(extensionId))
                .orElseThrow(() -> uploadNotFound(uploadId));

        return new UploadContext(upload, slot);
    }

    private NoSuchElementException uploadNotFound(UUID uploadId) {
        return new NoSuchElementException("Upload not found: " + uploadId);
    }

    private String normalizeChecksum(String checksum) {
        return checksum == null ? null : checksum.toLowerCase(Locale.ROOT);
    }
}
