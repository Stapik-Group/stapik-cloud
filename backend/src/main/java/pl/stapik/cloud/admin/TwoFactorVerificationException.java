package pl.stapik.cloud.admin;

public class TwoFactorVerificationException extends RuntimeException {

    public TwoFactorVerificationException() {
        super("Invalid password or verification code");
    }
}
