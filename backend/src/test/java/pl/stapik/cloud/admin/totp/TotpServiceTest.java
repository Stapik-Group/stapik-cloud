package pl.stapik.cloud.admin.totp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private static final String RFC_SECRET = Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
    private static final Instant NOW = Instant.ofEpochSecond(1_111_111_109L);

    private final TotpService totpService = new TotpService(Clock.fixed(NOW, ZoneOffset.UTC), "Stapik Cloud");

    @ParameterizedTest
    @CsvSource({
            "59,287082",
            "1111111109,081804",
            "1111111111,050471",
            "1234567890,005924",
            "2000000000,279037",
            "20000000000,353130"
    })
    void shouldMatchRfc6238Vectors(long epochSeconds, String expectedCode) {
        assertThat(totpService.codeFor(RFC_SECRET, Instant.ofEpochSecond(epochSeconds))).isEqualTo(expectedCode);
    }

    @Test
    void shouldAcceptCodeOfCurrentStepAndReturnItsNumber() {
        OptionalLong step = totpService.findMatchingStep(RFC_SECRET, "081804");

        assertThat(step).hasValue(NOW.getEpochSecond() / 30);
    }

    @Test
    void shouldAcceptCodesOneStepAroundNow() {
        String previous = totpService.codeFor(RFC_SECRET, NOW.minusSeconds(30));
        String next = totpService.codeFor(RFC_SECRET, NOW.plusSeconds(30));

        assertThat(totpService.findMatchingStep(RFC_SECRET, previous)).hasValue(NOW.getEpochSecond() / 30 - 1);
        assertThat(totpService.findMatchingStep(RFC_SECRET, next)).hasValue(NOW.getEpochSecond() / 30 + 1);
    }

    @Test
    void shouldRejectCodesTwoStepsAwayFromNow() {
        String tooOld = totpService.codeFor(RFC_SECRET, NOW.minusSeconds(60));
        String tooNew = totpService.codeFor(RFC_SECRET, NOW.plusSeconds(60));

        assertThat(totpService.findMatchingStep(RFC_SECRET, tooOld)).isEmpty();
        assertThat(totpService.findMatchingStep(RFC_SECRET, tooNew)).isEmpty();
    }

    @Test
    void shouldIgnoreSpacesInsideTheCode() {
        assertThat(totpService.findMatchingStep(RFC_SECRET, "081 804")).isPresent();
    }

    @Test
    void shouldRejectMalformedCodes() {
        assertThat(totpService.findMatchingStep(RFC_SECRET, null)).isEmpty();
        assertThat(totpService.findMatchingStep(RFC_SECRET, "")).isEmpty();
        assertThat(totpService.findMatchingStep(RFC_SECRET, "08180")).isEmpty();
        assertThat(totpService.findMatchingStep(RFC_SECRET, "0818040")).isEmpty();
        assertThat(totpService.findMatchingStep(RFC_SECRET, "08180a")).isEmpty();
    }

    @Test
    void shouldCreateRandomTwentyByteSecrets() {
        String first = totpService.newSecret();
        String second = totpService.newSecret();

        assertThat(first).hasSize(32).isNotEqualTo(second);
        assertThat(Base32.decode(first)).hasSize(20);
    }

    @Test
    void shouldBuildProvisioningUriWithEscapedLabel() {
        String uri = totpService.provisioningUri("jan kowalski@example.com", "ABCDEFGH");

        assertThat(uri).isEqualTo("otpauth://totp/Stapik%20Cloud:jan%20kowalski%40example.com"
                + "?secret=ABCDEFGH&issuer=Stapik%20Cloud&algorithm=SHA1&digits=6&period=30");
    }
}
