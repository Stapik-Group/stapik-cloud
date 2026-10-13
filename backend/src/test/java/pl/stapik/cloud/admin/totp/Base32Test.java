package pl.stapik.cloud.admin.totp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32Test {

    @ParameterizedTest
    @CsvSource({
            "f,MY",
            "fo,MZXQ",
            "foo,MZXW6",
            "foob,MZXW6YQ",
            "fooba,MZXW6YTB",
            "foobar,MZXW6YTBOI"
    })
    void shouldMatchRfc4648Vectors(String plain, String encoded) {
        assertThat(Base32.encode(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(plain.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void shouldEncodeEmptyInput() {
        assertThat(Base32.encode(new byte[0])).isEmpty();
        assertThat(Base32.decode("")).isEmpty();
    }

    @Test
    void shouldDecodeLowerCasePaddingAndSpaces() {
        assertThat(Base32.decode("mzxw 6ytb oi======")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void shouldRoundTripRandomData() {
        Random random = new Random(11);
        for (int length = 0; length < 40; length++) {
            byte[] data = new byte[length];
            random.nextBytes(data);

            assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
        }
    }

    @Test
    void shouldRejectCharactersOutsideTheAlphabet() {
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class);
    }
}
