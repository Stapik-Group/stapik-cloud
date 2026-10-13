package pl.stapik.cloud.admin.totp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QrCodeRenderer {

    private static final int QUIET_ZONE_MODULES = 2;

    public String toSvg(String content) {
        BitMatrix matrix = encode(content);
        int width = matrix.getWidth();
        int height = matrix.getHeight();

        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int runStart = x;
                while (x < width && matrix.get(x, y)) {
                    x++;
                }
                path.append('M').append(runStart).append(' ').append(y).append('h').append(x - runStart).append("v1h-").append(x - runStart).append('z');
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                + "\" shape-rendering=\"crispEdges\"><rect width=\"" + width + "\" height=\"" + height
                + "\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }

    private BitMatrix encode(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, QUIET_ZONE_MODULES));
        } catch (WriterException e) {
            throw new IllegalStateException("Cannot render the QR code", e);
        }
    }
}
