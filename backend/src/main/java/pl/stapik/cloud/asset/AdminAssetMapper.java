package pl.stapik.cloud.asset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.admin.data.AdminAssetResponse;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.common.mapper.DateTimeMapper;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class}
)
public interface AdminAssetMapper {
    @Mapping(target = "previewable", expression = "java(AssetPreviewPolicy.isPreviewable(assetData.getMimeType()))")
    AdminAssetResponse toResponse(AssetData assetData);
}
