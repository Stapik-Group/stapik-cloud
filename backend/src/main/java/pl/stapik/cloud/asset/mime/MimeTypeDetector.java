package pl.stapik.cloud.asset.mime;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MimeTypeDetector {

    private final Tika tika = new Tika();

    public String detect(byte[] contentPrefix, String filename) {
        return tika.detect(contentPrefix, filename).toLowerCase(Locale.ROOT);
    }
}
