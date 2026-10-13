package pl.stapik.cloud.document;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AdminDocumentsApiDelegate;
import pl.stapik.cloud.admin.data.AdminDocumentDiffResponse;
import pl.stapik.cloud.admin.data.AdminDocumentPartitionListResponse;
import pl.stapik.cloud.admin.data.AdminDocumentPartitionResponse;
import pl.stapik.cloud.admin.data.AdminDocumentResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponseVersionsInner;
import pl.stapik.cloud.admin.data.AdminDocumentWriteRequest;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentComparison;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.document.dto.DocumentWriter;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminDocumentDelegate implements AdminDocumentsApiDelegate {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    @Override
    public ResponseEntity<AdminDocumentResponse> getDocumentContent(UUID extensionId, String slotKey, String partition) {
        DocumentData document = documentService.getCurrent(identifier(extensionId, slotKey, partition));
        return adminDocumentResponse(document, slotKey);
    }

    @Override
    public ResponseEntity<AdminDocumentResponse> updateDocumentContent(UUID extensionId, String slotKey, AdminDocumentWriteRequest request, String ifMatch, String partition) {
        DocumentData updated = documentService.updateContent(
                identifier(extensionId, slotKey, partition),
                request.getContent(),
                DocumentETag.parse(ifMatch)
        );
        return adminDocumentResponse(updated, slotKey);
    }

    @Override
    public ResponseEntity<AdminDocumentVersionListResponse> listDocumentVersionsAdmin(UUID extensionId, String slotKey, String partition) {
        List<AdminDocumentVersionListResponseVersionsInner> versions = documentService.listVersions(identifier(extensionId, slotKey, partition)).stream()
                .map(documentMapper::toAdminVersionResponse)
                .toList();

        return ResponseEntity.ok(new AdminDocumentVersionListResponse().versions(versions));
    }

    @Override
    public ResponseEntity<AdminDocumentDiffResponse> compareDocumentVersionsAdmin(UUID extensionId, String slotKey, UUID from, UUID to, String partition) {
        DocumentComparison comparison = documentService.compareVersions(identifier(extensionId, slotKey, partition), from, to);
        return ResponseEntity.ok(documentMapper.toAdminDiffResponse(comparison));
    }

    @Override
    public ResponseEntity<AdminDocumentResponse> restoreDocumentVersionAdmin(UUID extensionId, String slotKey, UUID versionId, String partition) {
        DocumentData restored = documentService.restoreVersion(identifier(extensionId, slotKey, partition), versionId, DocumentWriter.NONE);
        return adminDocumentResponse(restored, slotKey);
    }

    @Override
    public ResponseEntity<AdminDocumentPartitionListResponse> listDocumentPartitionsAdmin(UUID extensionId, String slotKey) {
        List<AdminDocumentPartitionResponse> partitions = documentService.listPartitions(DocumentIdentifier.of(extensionId, slotKey)).stream()
                .map(documentMapper::toAdminPartitionResponse)
                .toList();

        return ResponseEntity.ok(new AdminDocumentPartitionListResponse().partitions(partitions));
    }

    private DocumentIdentifier identifier(UUID extensionId, String slotKey, String partition) {
        return partition == null || partition.isEmpty()
                ? DocumentIdentifier.of(extensionId, slotKey)
                : DocumentIdentifier.of(extensionId, slotKey, partition);
    }

    private ResponseEntity<AdminDocumentResponse> adminDocumentResponse(DocumentData documentData, String slotKey) {
        return ResponseEntity.ok()
                .eTag(DocumentETag.format(documentData.getContentHash()))
                .body(documentMapper.toAdminDocumentResponse(documentData, slotKey));
    }
}
