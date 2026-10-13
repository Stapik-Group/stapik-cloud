package pl.stapik.cloud.admin.totp;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeRendererTest {

    private final QrCodeRenderer renderer = new QrCodeRenderer();

    @Test
    void shouldRenderWellFormedSquareSvg() throws Exception {
        String svg = renderer.toSvg("otpauth://totp/Stapik%20Cloud:admin?secret=ABCDEFGH&issuer=Stapik%20Cloud");

        var root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(svg))).getDocumentElement();
        String[] viewBox = root.getAttribute("viewBox").split(" ");

        assertThat(root.getTagName()).isEqualTo("svg");
        assertThat(viewBox[2]).isEqualTo(viewBox[3]);
        assertThat(Integer.parseInt(viewBox[2])).isGreaterThanOrEqualTo(21 + 2 * 2);
        assertThat(root.getElementsByTagName("path").item(0).getAttributes().getNamedItem("d").getNodeValue()).startsWith("M");
    }

    @Test
    void shouldRenderDifferentContentDifferently() {
        assertThat(renderer.toSvg("first")).isNotEqualTo(renderer.toSvg("second"));
    }
}
