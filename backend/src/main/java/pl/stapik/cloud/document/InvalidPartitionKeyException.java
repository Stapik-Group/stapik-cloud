package pl.stapik.cloud.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPartitionKeyException extends RuntimeException {

    public InvalidPartitionKeyException(String partitionKey) {
        super("Invalid partition key (allowed: letters, digits, '.', '_' and '-', up to 100 characters, "
                + "starting with a letter or digit): " + partitionKey);
    }
}
