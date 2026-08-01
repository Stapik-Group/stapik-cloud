package pl.stapik.cloud.common.mapper;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

@Component
public class JsonNullableMapper {
    public <T> T unwrap(JsonNullable<T> wrapped) {
        return wrapped == null ? null : wrapped.orElse(null);
    }

    public <T> JsonNullable<T> wrap(T value) {
        return JsonNullable.of(value);
    }
}
