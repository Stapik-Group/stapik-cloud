package pl.stapik.cloud.dashboard.dto;

import pl.stapik.cloud.apikey.dto.ApiKeyActivity;
import pl.stapik.cloud.document.dto.DocumentConflictSummary;

import java.util.List;

public record DashboardStats(
        long documentStorageBytes,
        long assetStorageBytes,
        long documentCount,
        long assetCount,
        List<ApiKeyActivity> apiKeyActivity,
        List<DocumentConflictSummary> recentConflicts,
        List<DailyWrites> writesPerDay
) {
}
