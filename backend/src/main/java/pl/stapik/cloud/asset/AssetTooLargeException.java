package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
public class AssetTooLargeException extends RuntimeException {
    public AssetTooLargeException(long maxSizeBytes, long actualSizeBytes) {
        super("Asset size %d bytes exceeds slot limit of %d bytes".formatted(actualSizeBytes, maxSizeBytes));
    }
}