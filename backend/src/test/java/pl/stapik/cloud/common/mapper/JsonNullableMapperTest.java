package pl.stapik.cloud.common.mapper;

import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNullableMapperTest {

    private final JsonNullableMapper mapper = new JsonNullableMapper();

    @Test
    void shouldUnwrapPresentValue() {
        // given
        JsonNullable<String> wrapped = JsonNullable.of("test-value");

        // when
        String result = mapper.unwrap(wrapped);

        // then
        assertThat(result).isEqualTo("test-value");
    }

    @Test
    void shouldReturnNullWhenWrappedIsNull() {
        // when
        String result = mapper.unwrap(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenWrappedIsUndefined() {
        // given
        JsonNullable<String> wrapped = JsonNullable.undefined();

        // when
        String result = mapper.unwrap(wrapped);

        // then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenWrappedContainsNull() {
        // given
        JsonNullable<String> wrapped = JsonNullable.of(null);

        // when
        String result = mapper.unwrap(wrapped);

        // then
        assertThat(result).isNull();
    }
}