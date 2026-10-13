package pl.stapik.cloud.asset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.asset.dto.AssetUploadResult;
import pl.stapik.cloud.asset.dto.StartAssetUpload;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.internal.data.AssetUploadResponse;
import pl.stapik.cloud.internal.data.CreateAssetUploadRequest;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, AssetMapper.class}
)
public interface AssetUploadMapper {
    StartAssetUpload toCommand(CreateAssetUploadRequest request);

    @Mapping(target = "uploadId", source = "upload.id")
    @Mapping(target = "filename", source = "upload.filename")
    @Mapping(target = "sizeBytes", source = "upload.sizeBytes")
    @Mapping(target = "offset", source = "upload.receivedBytes")
    @Mapping(target = "expiresAt", source = "upload.expiresAt")
    AssetUploadResponse toResponse(AssetUploadResult result);
}
