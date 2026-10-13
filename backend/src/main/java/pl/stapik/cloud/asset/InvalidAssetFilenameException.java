package pl.stapik.cloud.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAssetFilenameException extends RuntimeException {
    public InvalidAssetFilenameException(String reason) {
        super("Invalid filename: " + reason);
    }
}
