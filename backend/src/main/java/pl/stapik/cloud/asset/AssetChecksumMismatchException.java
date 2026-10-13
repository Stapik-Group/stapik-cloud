package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class AssetChecksumMismatchException extends RuntimeException {
    public AssetChecksumMismatchException(String expectedChecksum, String actualChecksum) {
        super("Uploaded content checksum %s does not match the expected %s".formatted(actualChecksum, expectedChecksum));
    }
}
