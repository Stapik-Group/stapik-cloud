package pl.stapik.cloud.extension;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.admin.data.CreateExtensionRequest;
import pl.stapik.cloud.admin.data.ExtensionResponse;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, JsonNullableMapper.class}
)
public interface ExtensionMapper {
    ExtensionResponse toResponse(ExtensionData extension);
    ExtensionData toEntity(CreateExtensionRequest extensionRequest);
}
