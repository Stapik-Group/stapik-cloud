package pl.stapik.cloud.document;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.AdminDocumentsApiDelegate;
import pl.stapik.cloud.admin.data.AdminDocumentResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponse;
import pl.stapik.cloud.admin.data.AdminDocumentVersionListResponseVersionsInner;
import pl.stapik.cloud.admin.data.AdminDocumentWriteRequest;
import pl.stapik.cloud.document.data.DocumentData;
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
    public ResponseEntity<AdminDocumentResponse> getDocumentContent(UUID extensionId, String slotKey) {
        DocumentData document = documentService.getCurrent(DocumentIdentifier.of(extensionId, slotKey));
        return adminDocumentResponse(document, slotKey);
    }

    @Override
    public ResponseEntity<AdminDocumentResponse> updateDocumentContent(UUID extensionId, String slotKey, AdminDocumentWriteRequest request, String ifMatch) {
        DocumentData updated = documentService.updateContent(
                DocumentIdentifier.of(extensionId, slotKey),
                request.getContent(),
                DocumentETag.parse(ifMatch)
        );
        return adminDocumentResponse(updated, slotKey);
    }

    @Override
    public ResponseEntity<AdminDocumentVersionListResponse> listDocumentVersionsAdmin(UUID extensionId, String slotKey) {
        List<AdminDocumentVersionListResponseVersionsInner> versions = documentService.listVersions(DocumentIdentifier.of(extensionId, slotKey)).stream()
                .map(documentMapper::toAdminVersionResponse)
                .toList();

        return ResponseEntity.ok(new AdminDocumentVersionListResponse().versions(versions));
    }

    @Override
    public ResponseEntity<AdminDocumentResponse> restoreDocumentVersionAdmin(UUID extensionId, String slotKey, UUID versionId) {
        DocumentData restored = documentService.restoreVersion(DocumentIdentifier.of(extensionId, slotKey), versionId, DocumentWriter.NONE);
        return adminDocumentResponse(restored, slotKey);
    }

    private ResponseEntity<AdminDocumentResponse> adminDocumentResponse(DocumentData documentData, String slotKey) {
        return ResponseEntity.ok()
                .eTag(DocumentETag.format(documentData.getContentHash()))
                .body(documentMapper.toAdminDocumentResponse(documentData, slotKey));
    }
}
