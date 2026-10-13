package pl.stapik.cloud.dashboard.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.asset.AssetRepository;
import pl.stapik.cloud.dashboard.DashboardService;
import pl.stapik.cloud.dashboard.dto.DailyWrites;
import pl.stapik.cloud.dashboard.dto.DashboardStats;
import pl.stapik.cloud.document.DocumentRepository;
import pl.stapik.cloud.document.DocumentVersionRepository;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DailyWriteTotal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int WRITES_WINDOW_DAYS = 30;
    private static final Pageable RECENT_ITEMS = PageRequest.ofSize(10);

    private final DocumentRepository documentRepository;
    private final AssetRepository assetRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        return new DashboardStats(
                documentRepository.sumActiveSizeBytes(),
                assetRepository.sumSizeBytes(),
                documentRepository.countByDeletedAtIsNull(),
                assetRepository.count(),
                apiKeyRepository.findMostRecentlyUsed(RECENT_ITEMS),
                documentVersionRepository.findRecentByReason(VersionReason.CONFLICT_DISCARDED, RECENT_ITEMS),
                writesPerDay()
        );
    }

    private List<DailyWrites> writesPerDay() {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(WRITES_WINDOW_DAYS - 1L);

        Map<LocalDate, Long> totalsByDay = documentVersionRepository
                .countWritesPerDaySince(firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)).stream()
                .collect(Collectors.toMap(total -> LocalDate.parse(total.getDay()), DailyWriteTotal::getTotal));

        return firstDay.datesUntil(today.plusDays(1))
                .map(day -> new DailyWrites(day, totalsByDay.getOrDefault(day, 0L)))
                .toList();
    }
}
