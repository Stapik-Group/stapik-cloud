package pl.stapik.cloud.asset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssetPreviewPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"})
    void shouldAllowPreviewOfImagesAndPdf(String mimeType) {
        assertThat(AssetPreviewPolicy.isPreviewable(mimeType)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "text/html", "application/xhtml+xml", "text/plain", "application/octet-stream"})
    void shouldRejectPreviewOfTypesThatCouldExecuteScripts(String mimeType) {
        assertThat(AssetPreviewPolicy.isPreviewable(mimeType)).isFalse();
    }
}
