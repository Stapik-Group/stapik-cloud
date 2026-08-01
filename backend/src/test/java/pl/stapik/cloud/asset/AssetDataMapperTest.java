package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.common.mapper.DateTimeMapperImpl;
import pl.stapik.cloud.internal.data.AssetMetadataResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AssetDataMapperTest {

    @Spy
    private DateTimeMapperImpl dateTimeMapper;

    @InjectMocks
    private AssetMapperImpl assetMapper;

    @Test
    void shouldMapAssetToResponse() {
        // Given
        Instant updatedAt = Instant.parse("2026-07-18T10:00:00Z");

        AssetData assetData = AssetData.builder()
                .id(UUID.randomUUID())
                .documentSlotId(UUID.randomUUID())
                .filename("report.pdf")
                .mimeType("application/pdf")
                .sizeBytes(204800L)
                .storagePath("/data/assets/report.pdf")
                .checksumSha256("abc123hash")
                .createdAt(Instant.parse("2026-07-17T09:00:00Z"))
                .updatedAt(updatedAt)
                .build();

        // When
        AssetMetadataResponse response = assetMapper.toResponse(assetData);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFilename()).isEqualTo("report.pdf");
        assertThat(response.getMimeType()).isEqualTo("application/pdf");
        assertThat(response.getSizeBytes()).isEqualTo(204800L);
        assertThat(response.getChecksumSha256()).isEqualTo("abc123hash");
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldReturnNullWhenMappingNullAsset() {
        // When
        AssetMetadataResponse response = assetMapper.toResponse(null);

        // Then
        assertThat(response).isNull();
    }
}