package pl.stapik.cloud.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.asset.mime.MimeTypeDetector;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

import java.io.IOException;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class AssetMimeTypePolicy {

    private static final int INSPECTED_PREFIX_BYTES = 8192;

    private final MimeTypeDetector mimeTypeDetector;

    public String resolve(DocumentSlotData slot, String filename, InputStream content) throws IOException {
        content.mark(INSPECTED_PREFIX_BYTES);
        byte[] prefix = content.readNBytes(INSPECTED_PREFIX_BYTES);
        content.reset();

        String detectedMimeType = mimeTypeDetector.detect(prefix, filename);
        if (!slot.allowsMimeType(detectedMimeType)) {
            throw new AssetMimeTypeNotAllowedException(detectedMimeType);
        }
        return detectedMimeType;
    }
}
