package pl.stapik.cloud.admin;

public class PasswordVerificationException extends RuntimeException {

    public PasswordVerificationException() {
        super("Invalid current password");
    }
}
