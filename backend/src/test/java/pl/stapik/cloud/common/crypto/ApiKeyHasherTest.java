package pl.stapik.cloud.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final ApiKeyHasher hasher = new ApiKeyHasher(new HashingService(bcrypt));

    @Test
    void shouldProduceDeterministicSha256Hex() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldMatchKeyAgainstItsHash() {
        assertThat(hasher.matches("secret-key", hasher.hash("secret-key"))).isTrue();
    }

    @Test
    void shouldNotMatchDifferentKey() {
        assertThat(hasher.matches("other-key", hasher.hash("secret-key"))).isFalse();
    }

    @Test
    void shouldMatchLegacyBcryptHash() {
        String legacyHash = bcrypt.encode("secret-key");

        assertThat(hasher.isLegacy(legacyHash)).isTrue();
        assertThat(hasher.matches("secret-key", legacyHash)).isTrue();
        assertThat(hasher.matches("other-key", legacyHash)).isFalse();
    }

    @Test
    void shouldNotTreatSha256HashAsLegacy() {
        assertThat(hasher.isLegacy(hasher.hash("secret-key"))).isFalse();
    }
}
