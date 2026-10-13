package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.asset.storage.AssetStorage;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetEncryptionRunnerTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetStorage assetStorage;

    @InjectMocks
    private AssetEncryptionRunner runner;

    @Test
    void shouldSecureEveryAssetFileEvenWhenOneOfThemFails() throws Exception {
        // given
        when(assetRepository.findAll()).thenReturn(List.of(
                AssetData.builder().storagePath("a/missing.bin").build(),
                AssetData.builder().storagePath("a/present.bin").build()));
        doThrow(new IOException("Asset file not found")).when(assetStorage).secure("a/missing.bin");

        // when
        runner.run(null);

        // then
        verify(assetStorage).secure("a/missing.bin");
        verify(assetStorage).secure("a/present.bin");
    }
}
