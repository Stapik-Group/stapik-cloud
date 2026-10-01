package pl.stapik.cloud.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DocumentModifiedException extends RuntimeException {

    public DocumentModifiedException(String slotKey) {
        super("Document was modified after it was loaded: " + slotKey);
    }
}
