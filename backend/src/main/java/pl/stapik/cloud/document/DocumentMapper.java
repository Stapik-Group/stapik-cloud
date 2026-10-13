package pl.stapik.cloud.document;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import pl.stapik.cloud.admin.data.AdminDocumentDiffResponse;
import pl.stapik.cloud.admin.data.AdminDocumentPartitionResponse;
import pl.stapik.cloud.admin.data.AdminDocumentResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponseVersionsInner;
import pl.stapik.cloud.common.mapper.DateTimeMapper;
import pl.stapik.cloud.common.mapper.JsonNullableMapper;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.dto.DocumentChangeSummary;
import pl.stapik.cloud.document.dto.DocumentComparison;
import pl.stapik.cloud.document.dto.DocumentPartitionSummary;
import pl.stapik.cloud.internal.data.DocumentChangeResponse;
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
    @Mapping(target = "encrypted", source = "document.content", qualifiedByName = "isEncrypted")
    AdminDocumentResponse toAdminDocumentResponse(DocumentData document, String slotKey);
    DocumentVersionResponse toVersionResponse(DocumentVersionData version);
    @Mapping(target = "partition", source = "partitionKey")
    DocumentPartitionResponse toPartitionResponse(DocumentPartitionSummary summary);
    @Mapping(target = "partition", source = "partitionKey")
    AdminDocumentPartitionResponse toAdminPartitionResponse(DocumentPartitionSummary summary);
    @Mapping(target = "partition", source = "partitionKey", qualifiedByName = "partitionOrNull")
    @Mapping(target = "deleted", expression = "java(change.getDeletedAt() != null)")
    DocumentChangeResponse toChangeResponse(DocumentChangeSummary change);
    @Named("partitionOrNull")
    default String partitionOrNull(String partitionKey) {
        return DocumentData.DEFAULT_PARTITION_KEY.equals(partitionKey) ? null : partitionKey;
    }
    @Named("isEncrypted")
    default boolean isEncrypted(String content) {
        return EncryptedDocumentContent.isEncrypted(content);
    }
    AdminDocumentVersionListResponseVersionsInner toAdminVersionResponse(DocumentVersionData documentVersionData);
    AdminDocumentDiffResponse toAdminDiffResponse(DocumentComparison comparison);
}