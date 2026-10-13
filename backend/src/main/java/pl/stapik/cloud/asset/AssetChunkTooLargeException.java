package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
public class AssetChunkTooLargeException extends RuntimeException {
    public AssetChunkTooLargeException(long maxChunkBytes) {
        super("Chunk exceeds the allowed size of %d bytes".formatted(maxChunkBytes));
    }
}
