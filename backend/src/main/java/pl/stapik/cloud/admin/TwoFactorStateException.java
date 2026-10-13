package pl.stapik.cloud.admin;

public class TwoFactorStateException extends RuntimeException {

    public TwoFactorStateException(String message) {
        super(message);
    }
}
