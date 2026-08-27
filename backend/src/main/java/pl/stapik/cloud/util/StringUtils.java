package pl.stapik.cloud.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtils {
    public static String generateTraceId() {
        return UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}
