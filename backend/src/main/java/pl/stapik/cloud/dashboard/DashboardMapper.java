package pl.stapik.cloud.dashboard;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.admin.data.DashboardApiKeyActivity;
import pl.stapik.cloud.admin.data.DashboardConflict;
import pl.stapik.cloud.admin.data.DashboardStatsResponse;
import pl.stapik.cloud.apikey.dto.ApiKeyActivity;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.dashboard.dto.DashboardStats;
import pl.stapik.cloud.document.DocumentMapper;
import pl.stapik.cloud.document.dto.DocumentConflictSummary;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, DocumentMapper.class}
)
public interface DashboardMapper {
    DashboardStatsResponse toResponse(DashboardStats stats);

    @Mapping(target = "scope", expression = "java(pl.stapik.cloud.admin.data.ApiKeyScope.fromValue(activity.getScope().name()))")
    DashboardApiKeyActivity toApiKeyActivity(ApiKeyActivity activity);

    @Mapping(target = "partition", source = "partitionKey", qualifiedByName = "partitionOrNull")
    DashboardConflict toConflict(DocumentConflictSummary conflict);
}
