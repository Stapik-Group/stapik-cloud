package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.dto.AssetIdentifier;
import pl.stapik.cloud.asset.impl.AssetServiceImpl;
import pl.stapik.cloud.asset.storage.AssetStorage;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetDataServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private DocumentSlotRepository documentSlotRepository;

    @Mock
    private AssetStorage assetStorage;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private AssetServiceImpl assetServiceImpl;

    private static final UUID EXTENSION_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final String SLOT_KEY = "slot-key";
    private static final String FILENAME = "report.pdf";

    private DocumentSlotData slot(long maxSizeBytes) {
        return DocumentSlotData.builder()
                .id(SLOT_ID)
                .extensionId(EXTENSION_ID)
                .slotKey(SLOT_KEY)
                .maxSizeBytes(maxSizeBytes)
                .build();
    }

    @Test
    void shouldListAssetsForSlot() {
        // given
        DocumentSlotData slot = slot(1024L);
        List<AssetData> assetData = List.of(AssetData.builder().id(UUID.randomUUID()).documentSlotId(SLOT_ID).build());

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotId(SLOT_ID)).thenReturn(assetData);

        // when
        List<AssetData> result = assetServiceImpl.list(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY));

        // then
        assertThat(result).isEqualTo(assetData);
    }

    @Test
    void shouldThrowWhenSlotNotFoundOnList() {
        // given
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.empty());

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY);
        assertThatThrownBy(() -> assetServiceImpl.list(assetIdentifier))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(assetRepository);
    }

    @Test
    void shouldUploadNewAsset() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetStorage.StoredAsset stored = new AssetStorage.StoredAsset("/data/assets/" + FILENAME, "hash123", 2048L);

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(multipartFile.getSize()).thenReturn(2048L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(assetStorage.store(EXTENSION_ID, SLOT_ID, FILENAME, multipartFile)).thenReturn(stored);
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.empty());
        when(assetRepository.save(any(AssetData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        AssetData result = assetServiceImpl.upload(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME), multipartFile);

        // then
        assertThat(result.getDocumentSlotId()).isEqualTo(SLOT_ID);
        assertThat(result.getFilename()).isEqualTo(FILENAME);
        assertThat(result.getMimeType()).isEqualTo("application/pdf");
        assertThat(result.getSizeBytes()).isEqualTo(2048L);
        assertThat(result.getStoragePath()).isEqualTo("/data/assets/" + FILENAME);
        assertThat(result.getChecksumSha256()).isEqualTo("hash123");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldDefaultToOctetStreamWhenContentTypeIsNull() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetStorage.StoredAsset stored = new AssetStorage.StoredAsset("/data/assets/" + FILENAME, "hash123", 2048L);

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(multipartFile.getSize()).thenReturn(2048L);
        when(multipartFile.getContentType()).thenReturn(null);
        when(assetStorage.store(EXTENSION_ID, SLOT_ID, FILENAME, multipartFile)).thenReturn(stored);
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.empty());
        when(assetRepository.save(any(AssetData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        AssetData result = assetServiceImpl.upload(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME), multipartFile);

        // then
        assertThat(result.getMimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldOverwriteExistingAssetOnUpload() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        UUID existingAssetId = UUID.randomUUID();
        Instant originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        AssetData existing = AssetData.builder()
                .id(existingAssetId)
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .mimeType("text/plain")
                .sizeBytes(100L)
                .storagePath("/old/path")
                .checksumSha256("old-hash")
                .createdAt(originalCreatedAt)
                .updatedAt(originalCreatedAt)
                .build();

        AssetStorage.StoredAsset stored = new AssetStorage.StoredAsset("/data/assets/" + FILENAME, "new-hash", 4096L);

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(multipartFile.getSize()).thenReturn(4096L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(assetStorage.store(EXTENSION_ID, SLOT_ID, FILENAME, multipartFile)).thenReturn(stored);
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(AssetData.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        AssetData result = assetServiceImpl.upload(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME), multipartFile);

        // then
        assertThat(result.getId()).isEqualTo(existingAssetId);
        assertThat(result.getStoragePath()).isEqualTo("/data/assets/" + FILENAME);
        assertThat(result.getChecksumSha256()).isEqualTo("new-hash");
        assertThat(result.getSizeBytes()).isEqualTo(4096L);
        assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(result.getUpdatedAt()).isAfter(originalCreatedAt);
    }

    @Test
    void shouldThrowWhenFileExceedsMaxSize() {
        // given
        DocumentSlotData slot = slot(1000L);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(multipartFile.getSize()).thenReturn(2000L);

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.upload(assetIdentifier, multipartFile))
                .isInstanceOf(AssetTooLargeException.class)
                .hasMessageContaining("2000")
                .hasMessageContaining("1000");

        verifyNoInteractions(assetStorage);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void shouldWrapIOExceptionOnUploadStoreFailure() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(multipartFile.getSize()).thenReturn(2048L);
        when(assetStorage.store(EXTENSION_ID, SLOT_ID, FILENAME, multipartFile))
                .thenThrow(new IOException("disk full"));

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.upload(assetIdentifier, multipartFile))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(FILENAME)
                .cause()
                .hasMessageContaining("disk full");

        verify(assetRepository, never()).save(any());
    }

    @Test
    void shouldDownloadAsset() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetData assetData = AssetData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .storagePath("/data/assets/" + FILENAME)
                .build();
        Resource resource = mock(Resource.class);

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.of(assetData));
        when(assetStorage.load("/data/assets/" + FILENAME)).thenReturn(resource);

        // when
        Resource result = assetServiceImpl.download(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME));

        // then
        assertThat(result).isEqualTo(resource);
    }

    @Test
    void shouldThrowWhenAssetNotFoundOnDownload() {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.empty());

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.download(assetIdentifier))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(FILENAME);

        verifyNoInteractions(assetStorage);
    }

    @Test
    void shouldWrapIOExceptionOnDownloadFailure() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetData assetData = AssetData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .storagePath("/data/assets/" + FILENAME)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.of(assetData));
        when(assetStorage.load("/data/assets/" + FILENAME)).thenThrow(new IOException("read error"));

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.download(assetIdentifier))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(FILENAME)
                .cause()
                .hasMessageContaining("read error");
    }

    @Test
    void shouldDeleteAsset() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetData assetData = AssetData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .storagePath("/data/assets/" + FILENAME)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.of(assetData));

        // when
        assetServiceImpl.delete(AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME));

        // then
        verify(assetStorage).delete("/data/assets/" + FILENAME);
        verify(assetRepository).delete(assetData);
    }

    @Test
    void shouldThrowWhenAssetNotFoundOnDelete() {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.empty());

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.delete(assetIdentifier))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(assetStorage);
        verify(assetRepository, never()).delete(any());
    }

    @Test
    void shouldWrapIOExceptionOnDeleteFailureAndNotDeleteFromRepository() throws IOException {
        // given
        DocumentSlotData slot = slot(1_048_576L);
        AssetData assetData = AssetData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .storagePath("/data/assets/" + FILENAME)
                .build();

        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY))
                .thenReturn(Optional.of(slot));
        when(assetRepository.findByDocumentSlotIdAndFilename(SLOT_ID, FILENAME)).thenReturn(Optional.of(assetData));
        doThrow(new IOException("permission denied")).when(assetStorage).delete("/data/assets/" + FILENAME);

        // when / then
        AssetIdentifier assetIdentifier = AssetIdentifier.of(EXTENSION_ID, SLOT_KEY, FILENAME);
        assertThatThrownBy(() -> assetServiceImpl.delete(assetIdentifier))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(FILENAME)
                .cause()
                .hasMessageContaining("permission denied");

        verify(assetRepository, never()).delete(any());
    }
}