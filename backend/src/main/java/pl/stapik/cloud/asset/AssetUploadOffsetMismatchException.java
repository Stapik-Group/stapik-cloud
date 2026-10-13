package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AssetUploadOffsetMismatchException extends RuntimeException {
    public AssetUploadOffsetMismatchException(long expectedOffset, long actualOffset) {
        super("Upload expects the next chunk at offset %d, got %d".formatted(expectedOffset, actualOffset));
    }
}
