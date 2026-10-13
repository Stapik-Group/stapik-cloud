package pl.stapik.cloud.admin.totp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;

@Component
public class TotpService {

    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final int CODE_MODULUS = 1_000_000;
    private static final int PERIOD_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final Clock clock;
    private final String issuer;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(Clock clock, @Value("${stapik-cloud.security.totp.issuer:Stapik Cloud}") String issuer) {
        this.clock = clock;
        this.issuer = issuer;
    }

    public String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return Base32.encode(secret);
    }

    public String provisioningUri(String accountName, String secret) {
        return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(accountName)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + CODE_DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    public OptionalLong findMatchingStep(String secret, String code) {
        return findMatchingStep(secret, code, clock.instant());
    }

    public OptionalLong findMatchingStep(String secret, String code, Instant at) {
        String candidate = code == null ? "" : code.replace(" ", "");
        if (!isSixDigits(candidate)) {
            return OptionalLong.empty();
        }

        byte[] key = Base32.decode(secret);
        long currentStep = stepAt(at);
        for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
            if (MessageDigest.isEqual(codeAt(key, step).getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public String codeFor(String secret, Instant at) {
        return codeAt(Base32.decode(secret), stepAt(at));
    }

    private static long stepAt(Instant at) {
        return at.getEpochSecond() / PERIOD_SECONDS;
    }

    private static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());

            int offset = hash[hash.length - 1] & 0x0f;
            int truncated = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%0" + CODE_DIGITS + "d", truncated % CODE_MODULUS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is not available", e);
        }
    }

    private static boolean isSixDigits(String candidate) {
        return candidate.length() == CODE_DIGITS && candidate.chars().allMatch(character -> character >= '0' && character <= '9');
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
