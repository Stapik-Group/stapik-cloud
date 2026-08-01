package pl.stapik.cloud.common.mapper;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeMapperTest {

    private final DateTimeMapper mapper = new DateTimeMapperImpl();

    @Test
    void shouldMapInstantToOffsetDateTime() {
        // given
        Instant now = Instant.parse("2026-07-18T18:06:44Z");

        // when
        OffsetDateTime result = mapper.toOffsetDateTime(now);

        // then
        assertThat(result).isEqualTo(now.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldReturnNullWhenMappingNullInstant() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }

    @Test
    void shouldMapOffsetDateTimeToInstant() {
        // given
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 18, 18, 6, 44, 0, ZoneOffset.UTC);

        // when
        Instant result = mapper.toInstant(now);

        // then
        assertThat(result).isEqualTo(now.toInstant());
    }

    @Test
    void shouldReturnNullWhenMappingNullOffsetDateTime() {
        assertThat(mapper.toInstant(null)).isNull();
    }
}