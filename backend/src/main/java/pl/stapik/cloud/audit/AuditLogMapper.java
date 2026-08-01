package pl.stapik.cloud.audit;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.admin.data.AuditLogEntryResponse;
import pl.stapik.cloud.audit.data.AuditLogEntryData;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, JsonNullableMapper.class}
)
public interface AuditLogMapper {
    AuditLogEntryResponse toResponse(AuditLogEntryData entry);
}