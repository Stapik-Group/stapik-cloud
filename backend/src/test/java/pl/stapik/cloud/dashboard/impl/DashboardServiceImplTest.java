package pl.stapik.cloud.dashboard.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.apikey.dto.ApiKeyActivity;
import pl.stapik.cloud.asset.AssetRepository;
import pl.stapik.cloud.dashboard.dto.DailyWrites;
import pl.stapik.cloud.dashboard.dto.DashboardStats;
import pl.stapik.cloud.document.DocumentRepository;
import pl.stapik.cloud.document.DocumentVersionRepository;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DailyWriteTotal;
import pl.stapik.cloud.document.dto.DocumentConflictSummary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void shouldCollectCountsAndStorageFromRepositories() {
        // given
        ApiKeyActivity keyActivity = mock(ApiKeyActivity.class);
        DocumentConflictSummary conflict = mock(DocumentConflictSummary.class);
        given(documentRepository.sumActiveSizeBytes()).willReturn(2048L);
        given(assetRepository.sumSizeBytes()).willReturn(4096L);
        given(documentRepository.countByDeletedAtIsNull()).willReturn(7L);
        given(assetRepository.count()).willReturn(3L);
        given(apiKeyRepository.findMostRecentlyUsed(any())).willReturn(List.of(keyActivity));
        given(documentVersionRepository.findRecentByReason(eq(VersionReason.CONFLICT_DISCARDED), any()))
                .willReturn(List.of(conflict));

        // when
        DashboardStats stats = dashboardService.getStats();

        // then
        assertThat(stats.documentStorageBytes()).isEqualTo(2048L);
        assertThat(stats.assetStorageBytes()).isEqualTo(4096L);
        assertThat(stats.documentCount()).isEqualTo(7L);
        assertThat(stats.assetCount()).isEqualTo(3L);
        assertThat(stats.apiKeyActivity()).containsExactly(keyActivity);
        assertThat(stats.recentConflicts()).containsExactly(conflict);
    }

    @Test
    void shouldFillDaysWithoutWritesWithZero() {
        // given
        given(documentVersionRepository.countWritesPerDaySince(Instant.parse("2026-09-14T00:00:00Z")))
                .willReturn(List.of(
                        writeTotal("2026-09-14", 4),
                        writeTotal("2026-10-13", 9)
                ));

        // when
        List<DailyWrites> writes = dashboardService.getStats().writesPerDay();

        // then
        assertThat(writes).hasSize(30);
        assertThat(writes.getFirst()).isEqualTo(new DailyWrites(LocalDate.parse("2026-09-14"), 4));
        assertThat(writes.getLast()).isEqualTo(new DailyWrites(LocalDate.parse("2026-10-13"), 9));
        assertThat(writes.subList(1, 29)).allMatch(day -> day.count() == 0);
    }

    private DailyWriteTotal writeTotal(String day, long total) {
        return new DailyWriteTotal() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
