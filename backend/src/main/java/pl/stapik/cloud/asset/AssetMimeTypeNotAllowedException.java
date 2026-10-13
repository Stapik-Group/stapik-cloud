package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class AssetMimeTypeNotAllowedException extends RuntimeException {
    public AssetMimeTypeNotAllowedException(String detectedMimeType) {
        super("Detected content type %s is not allowed in this slot".formatted(detectedMimeType));
    }
}
