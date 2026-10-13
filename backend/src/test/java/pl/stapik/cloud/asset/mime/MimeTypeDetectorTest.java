package pl.stapik.cloud.asset.mime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MimeTypeDetectorTest {

    private final MimeTypeDetector detector = new MimeTypeDetector();

    @Test
    void shouldDetectPngByMagicBytesIgnoringMisleadingExtension() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        assertThat(detector.detect(png, "photo.txt")).isEqualTo("image/png");
    }

    @Test
    void shouldDetectPdf() {
        byte[] pdf = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

        assertThat(detector.detect(pdf, "report.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void shouldNotTrustExtensionForExecutableContent() {
        byte[] elf = {0x7F, 'E', 'L', 'F', 2, 1, 1, 0};

        assertThat(detector.detect(elf, "image.png")).isNotEqualTo("image/png");
    }
}
