package pl.stapik.cloud.asset;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.internal.data.AssetMetadataResponse;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class}
)
public interface AssetMapper {
    AssetMetadataResponse toResponse(AssetData assetData);
}