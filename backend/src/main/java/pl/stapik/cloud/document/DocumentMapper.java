package pl.stapik.cloud.document;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pl.stapik.cloud.admin.data.AdminDocumentResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponseVersionsInner;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;
import pl.stapik.cloud.internal.data.DocumentPartitionResponse;
import pl.stapik.cloud.internal.data.DocumentResponse;
import pl.stapik.cloud.internal.data.DocumentVersionResponse;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DateTimeMapper.class, JsonNullableMapper.class}
)
public interface DocumentMapper {
    @Mapping(target = "slotKey", source = "slotKey")
    @Mapping(target = "updatedAt", source = "document.updatedAt")
    DocumentResponse toResponse(DocumentData document, String slotKey);
    @Mapping(target = "slotKey", source = "slotKey")
    @Mapping(target = "updatedAt", source = "document.updatedAt")
    AdminDocumentResponse toAdminDocumentResponse(DocumentData document, String slotKey);
    DocumentVersionResponse toVersionResponse(DocumentVersionData version);
    @Mapping(target = "partition", source = "partitionKey")
    DocumentPartitionResponse toPartitionResponse(DocumentPartitionSummary summary);
    AdminDocumentVersionListResponseVersionsInner toAdminVersionResponse(DocumentVersionData documentVersionData);
}