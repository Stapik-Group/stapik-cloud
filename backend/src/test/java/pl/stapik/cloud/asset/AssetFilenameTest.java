package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetFilenameTest {

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "my file (1).txt", "zażółć gęślą.txt", ".hidden", "archive.tar.gz", "console.txt", "COM10.txt"})
    void shouldAcceptOrdinaryFilenames(String filename) {
        assertThat(AssetFilename.normalize(filename)).isEqualTo(filename);
    }

    @Test
    void shouldNormalizeToNfc() {
        String decomposed = "a\u0308.txt";

        assertThat(AssetFilename.normalize(decomposed)).isEqualTo("\u00e4.txt");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "a\0b.txt", "a\nb.txt", "a\tb.txt", "a/b.txt", "a\\b.txt", "a:b.txt", "a*.txt", "a?.txt",
            "a\"b.txt", "a<b>.txt", "a|b.txt", "..", ".", "name.", "name ", " name", "evil\u202Egnp.exe",
            "zero\u200Bwidth.txt", "line\u2028break.txt", "private\uE000.txt", "lone\uD800surrogate.txt"
    })
    void shouldRejectUnsafeFilenames(String filename) {
        assertThatThrownBy(() -> AssetFilename.normalize(filename)).isInstanceOf(InvalidAssetFilenameException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CON", "con.txt", "Nul.tar.gz", "AUX .txt", "COM1", "lpt9.log", "PRN.pdf"})
    void shouldRejectReservedDeviceNames(String filename) {
        assertThatThrownBy(() -> AssetFilename.normalize(filename))
                .isInstanceOf(InvalidAssetFilenameException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void shouldRejectFilenameLongerThan255Bytes() {
        assertThat(AssetFilename.normalize("a".repeat(255))).hasSize(255);
        assertThatThrownBy(() -> AssetFilename.normalize("a".repeat(256))).isInstanceOf(InvalidAssetFilenameException.class);
        assertThatThrownBy(() -> AssetFilename.normalize("ż".repeat(128))).isInstanceOf(InvalidAssetFilenameException.class);
    }
}
