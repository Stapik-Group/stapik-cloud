package pl.stapik.cloud.apikey;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pl.stapik.cloud.apikey.impl.ApiKeyGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();

    @Test
    void shouldGenerateKeyWithCorrectFormat() {
        // when
        ApiKeyGenerator.GeneratedKey generated = apiKeyGenerator.generate();

        // then
        assertThat(generated.rawKey()).hasSizeGreaterThan(8);
        assertThat(generated.prefix()).hasSize(8);
        assertThat(generated.rawKey()).startsWith(generated.prefix());
    }

    @RepeatedTest(10)
    void shouldGenerateUniqueKeys() {
        // when
        ApiKeyGenerator.GeneratedKey first = apiKeyGenerator.generate();
        ApiKeyGenerator.GeneratedKey second = apiKeyGenerator.generate();

        // then
        assertThat(first.rawKey()).isNotEqualTo(second.rawKey());
    }
}