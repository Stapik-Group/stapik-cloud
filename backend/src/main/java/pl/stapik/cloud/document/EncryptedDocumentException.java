package pl.stapik.cloud.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EncryptedDocumentException extends RuntimeException {

    public EncryptedDocumentException(String slotKey) {
        super("Document is encrypted end-to-end and cannot be edited or compared on the server: " + slotKey);
    }
}
