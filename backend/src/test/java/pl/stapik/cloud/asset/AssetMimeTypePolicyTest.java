package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.asset.mime.MimeTypeDetector;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetMimeTypePolicyTest {

    private static final String FILENAME = "file.bin";

    @Mock
    private MimeTypeDetector mimeTypeDetector;

    @InjectMocks
    private AssetMimeTypePolicy policy;

    private static DocumentSlotData slotAllowing(String... mimeTypes) {
        return DocumentSlotData.builder().allowedMimeTypes(List.of(mimeTypes)).build();
    }

    private static InputStream content() {
        return new BufferedInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}));
    }

    @Test
    void shouldAcceptAnyTypeWhenSlotHasNoWhitelist() throws IOException {
        when(mimeTypeDetector.detect(any(), eq(FILENAME))).thenReturn("image/png");

        assertThat(policy.resolve(slotAllowing(), FILENAME, content())).isEqualTo("image/png");
    }

    @Test
    void shouldAcceptExactMatch() throws IOException {
        when(mimeTypeDetector.detect(any(), eq(FILENAME))).thenReturn("application/pdf");

        assertThat(policy.resolve(slotAllowing("application/pdf"), FILENAME, content())).isEqualTo("application/pdf");
    }

    @Test
    void shouldAcceptWildcardMatch() throws IOException {
        when(mimeTypeDetector.detect(any(), eq(FILENAME))).thenReturn("image/webp");

        assertThat(policy.resolve(slotAllowing("image/*"), FILENAME, content())).isEqualTo("image/webp");
    }

    @Test
    void shouldRejectTypeOutsideWhitelist() {
        when(mimeTypeDetector.detect(any(), eq(FILENAME))).thenReturn("application/x-msdownload");

        assertThatThrownBy(() -> policy.resolve(slotAllowing("image/*"), FILENAME, content()))
                .isInstanceOf(AssetMimeTypeNotAllowedException.class)
                .hasMessageContaining("application/x-msdownload");
    }

    @Test
    void shouldLeaveContentReadableFromTheStartAfterInspection() throws IOException {
        when(mimeTypeDetector.detect(any(), eq(FILENAME))).thenReturn("image/png");
        InputStream content = content();

        policy.resolve(slotAllowing(), FILENAME, content);

        assertThat(content.readAllBytes()).containsExactly(1, 2, 3);
    }
}
