package pl.stapik.cloud.dashboard.dto;

import java.time.LocalDate;

public record DailyWrites(LocalDate date, long count) {
}
