package pl.stapik.cloud.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.data.AssetUploadData;
import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.asset.dto.StartAssetUpload;
import pl.stapik.cloud.asset.impl.AssetUploadServiceImpl;
import pl.stapik.cloud.asset.storage.AssetStorage;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetUploadServiceTest {

    private static final UUID EXTENSION_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final UUID UPLOAD_ID = UUID.randomUUID();
    private static final String SLOT_KEY = "files";
    private static final String FILENAME = "video.bin";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final long MAX_CHUNK_BYTES = 1024;

    @Mock
    private AssetUploadRepository assetUploadRepository;

    @Mock
    private DocumentSlotRepository documentSlotRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private AssetStorage assetStorage;

    private AssetUploadServiceImpl assetUploadService;

    @BeforeEach
    void setUp() {
        assetUploadService = new AssetUploadServiceImpl(
                assetUploadRepository,
                documentSlotRepository,
                assetService,
                assetStorage,
                new AssetUploadProperties("/api/v1/asset-uploads", SESSION_TTL, DataSize.ofBytes(MAX_CHUNK_BYTES)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private DocumentSlotData slot(long maxSizeBytes) {
        return DocumentSlotData.builder()
                .id(SLOT_ID)
                .extensionId(EXTENSION_ID)
                .slotKey(SLOT_KEY)
                .maxSizeBytes(maxSizeBytes)
                .build();
    }

    private AssetUploadData upload(long sizeBytes, long receivedBytes, String expectedChecksum) {
        return AssetUploadData.builder()
                .id(UPLOAD_ID)
                .documentSlotId(SLOT_ID)
                .filename(FILENAME)
                .mimeType("video/mp4")
                .sizeBytes(sizeBytes)
                .receivedBytes(receivedBytes)
                .expectedChecksumSha256(expectedChecksum)
                .expiresAt(NOW.plusSeconds(60))
                .build();
    }

    private void givenUploadForUpdate(AssetUploadData upload) {
        when(assetUploadRepository.findForUpdateById(UPLOAD_ID)).thenReturn(Optional.of(upload));
        when(documentSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(10_000L)));
    }

    private static InputStream chunk() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Test
    void shouldStartUploadWithDefaultMimeTypeAndNormalizedChecksum() throws Exception {
        // given
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY)).thenReturn(Optional.of(slot(10_000L)));
        when(assetUploadRepository.save(any(AssetUploadData.class))).thenAnswer(inv -> {
            AssetUploadData saved = inv.getArgument(0);
            saved.setId(UPLOAD_ID);
            return saved;
        });

        // when
        AssetUploadResult result = assetUploadService.start(EXTENSION_ID,
                new StartAssetUpload(SLOT_KEY, FILENAME, 5_000L, null, "ABCDEF"));

        // then
        assertThat(result.asset()).isNull();
        assertThat(result.upload().getReceivedBytes()).isZero();
        assertThat(result.upload().getMimeType()).isEqualTo("application/octet-stream");
        assertThat(result.upload().getExpectedChecksumSha256()).isEqualTo("abcdef");
        assertThat(result.upload().getExpiresAt()).isEqualTo(NOW.plus(SESSION_TTL));
        verify(assetStorage).beginUpload(UPLOAD_ID, EXTENSION_ID, SLOT_ID, FILENAME);
    }

    @Test
    void shouldRejectUploadLargerThanSlotLimit() {
        // given
        when(documentSlotRepository.findByExtensionIdAndSlotKey(EXTENSION_ID, SLOT_KEY)).thenReturn(Optional.of(slot(100L)));

        // when / then
        StartAssetUpload command = new StartAssetUpload(SLOT_KEY, FILENAME, 101L, null, null);
        assertThatThrownBy(() -> assetUploadService.start(EXTENSION_ID, command))
                .isInstanceOf(AssetTooLargeException.class);
        verifyNoInteractions(assetStorage);
    }

    @Test
    void shouldAdvanceOffsetAndExtendExpiryWhenChunkIsNotLast() throws Exception {
        // given
        AssetUploadData upload = upload(1_000L, 200L, null);
        givenUploadForUpdate(upload);
        when(assetStorage.appendChunk(eq(UPLOAD_ID), eq(200L), any(), eq(800L))).thenReturn(300L);

        // when
        AssetUploadResult result = assetUploadService.append(EXTENSION_ID, UPLOAD_ID, 200L, chunk());

        // then
        assertThat(result.asset()).isNull();
        assertThat(result.upload().getReceivedBytes()).isEqualTo(500L);
        assertThat(result.upload().getExpiresAt()).isEqualTo(NOW.plus(SESSION_TTL));
        verify(assetStorage, never()).commitUpload(any(), any(), any(), any(), any());
    }

    @Test
    void shouldLimitChunkToConfiguredMaximum() throws Exception {
        // given
        AssetUploadData upload = upload(1_000_000L, 0L, null);
        givenUploadForUpdate(upload);
        when(assetStorage.appendChunk(eq(UPLOAD_ID), eq(0L), any(), eq(MAX_CHUNK_BYTES))).thenReturn(MAX_CHUNK_BYTES);

        // when
        assetUploadService.append(EXTENSION_ID, UPLOAD_ID, 0L, chunk());

        // then
        verify(assetStorage).appendChunk(eq(UPLOAD_ID), eq(0L), any(), eq(MAX_CHUNK_BYTES));
    }

    @Test
    void shouldRejectChunkAtWrongOffset() throws Exception {
        // given
        givenUploadForUpdate(upload(1_000L, 200L, null));

        // when / then
        InputStream chunk = chunk();
        assertThatThrownBy(() -> assetUploadService.append(EXTENSION_ID, UPLOAD_ID, 100L, chunk))
                .isInstanceOf(AssetUploadOffsetMismatchException.class)
                .hasMessageContaining("200");
        verify(assetStorage, never()).appendChunk(any(), anyLong(), any(), anyLong());
    }

    @Test
    void shouldStoreAssetAndRemoveSessionWhenLastChunkArrives() throws Exception {
        // given
        AssetUploadData upload = upload(1_000L, 600L, "abc");
        givenUploadForUpdate(upload);
        AssetStorage.StoredAsset stored = new AssetStorage.StoredAsset("path/video.bin", "abc", 1_000L);
        AssetData asset = AssetData.builder().filename(FILENAME).build();
        when(assetStorage.appendChunk(eq(UPLOAD_ID), eq(600L), any(), eq(400L))).thenReturn(400L);
        when(assetStorage.commitUpload(UPLOAD_ID, EXTENSION_ID, SLOT_ID, FILENAME, "abc")).thenReturn(stored);
        when(assetService.saveStoredAsset(any(DocumentSlotData.class), eq(FILENAME), eq("video/mp4"), eq(stored))).thenReturn(asset);

        // when
        AssetUploadResult result = assetUploadService.append(EXTENSION_ID, UPLOAD_ID, 600L, chunk());

        // then
        assertThat(result.asset()).isSameAs(asset);
        assertThat(result.upload().getReceivedBytes()).isEqualTo(1_000L);
        verify(assetUploadRepository).delete(upload);
    }

    @Test
    void shouldDiscardUploadWhenChecksumDoesNotMatch() throws Exception {
        // given
        AssetUploadData upload = upload(1_000L, 600L, "abc");
        givenUploadForUpdate(upload);
        when(assetStorage.appendChunk(eq(UPLOAD_ID), eq(600L), any(), eq(400L))).thenReturn(400L);
        when(assetStorage.commitUpload(UPLOAD_ID, EXTENSION_ID, SLOT_ID, FILENAME, "abc"))
                .thenThrow(new AssetChecksumMismatchException("abc", "def"));

        // when / then
        InputStream chunk = chunk();
        assertThatThrownBy(() -> assetUploadService.append(EXTENSION_ID, UPLOAD_ID, 600L, chunk))
                .isInstanceOf(AssetChecksumMismatchException.class);
        verify(assetStorage).discardUpload(UPLOAD_ID);
        verify(assetUploadRepository).delete(upload);
        verifyNoInteractions(assetService);
    }

    @Test
    void shouldReportNotFoundForUploadOfAnotherExtension() {
        // given
        when(assetUploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload(1_000L, 0L, null)));
        when(documentSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(10_000L)));

        // when / then
        UUID otherExtensionId = UUID.randomUUID();
        assertThatThrownBy(() -> assetUploadService.get(otherExtensionId, UPLOAD_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldReportNotFoundForExpiredUpload() {
        // given
        AssetUploadData expired = upload(1_000L, 0L, null);
        expired.setExpiresAt(NOW);
        when(assetUploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(expired));

        // when / then
        assertThatThrownBy(() -> assetUploadService.get(EXTENSION_ID, UPLOAD_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldDiscardDataOnAbort() throws Exception {
        // given
        AssetUploadData upload = upload(1_000L, 0L, null);
        when(assetUploadRepository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
        when(documentSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(10_000L)));

        // when
        assetUploadService.abort(EXTENSION_ID, UPLOAD_ID);

        // then
        verify(assetStorage).discardUpload(UPLOAD_ID);
        verify(assetUploadRepository).delete(upload);
    }

    @Test
    void shouldDiscardExpiredUploads() throws Exception {
        // given
        AssetUploadData first = upload(1_000L, 0L, null);
        AssetUploadData second = upload(2_000L, 0L, null);
        second.setId(UUID.randomUUID());
        when(assetUploadRepository.findByExpiresAtBefore(NOW)).thenReturn(List.of(first, second));

        // when
        int deleted = assetUploadService.deleteExpired();

        // then
        assertThat(deleted).isEqualTo(2);
        verify(assetStorage).discardUpload(first.getId());
        verify(assetStorage).discardUpload(second.getId());
        verify(assetUploadRepository).delete(first);
        verify(assetUploadRepository).delete(second);
    }
}
