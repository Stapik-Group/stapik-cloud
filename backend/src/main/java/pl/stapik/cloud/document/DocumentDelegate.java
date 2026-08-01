package pl.stapik.cloud.document;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.document.dto.WriteResult;
import pl.stapik.cloud.internal.api.DocumentsApiDelegate;
import pl.stapik.cloud.internal.data.DocumentResponse;
import pl.stapik.cloud.internal.data.DocumentVersionListResponse;
import pl.stapik.cloud.internal.data.DocumentVersionResponse;
import pl.stapik.cloud.internal.data.DocumentWriteRequest;
import pl.stapik.cloud.security.apikey.ApiKeyPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentDelegate implements DocumentsApiDelegate {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    @Override
    public ResponseEntity<DocumentResponse> getDocument(String slotKey) {
        DocumentData documentData = documentService.getCurrent(DocumentIdentifier.of(currentExtensionId(), slotKey));
        return ResponseEntity.ok(documentMapper.toResponse(documentData, slotKey));
    }

    @Override
    public ResponseEntity<DocumentResponse> writeDocument(String slotKey, DocumentWriteRequest documentWriteRequest) {
        WriteResult result = documentService.write(
                DocumentIdentifier.of(currentExtensionId(), slotKey),
                documentWriteRequest.getContent(),
                documentWriteRequest.getClientLastKnownUpdate().toInstant()
        );

        DocumentResponse response = documentMapper.toResponse(result.documentData(), slotKey);
        return result.conflict()
                ? ResponseEntity.status(409).body(response)
                : ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteDocument(String slotKey) {
        documentService.delete(DocumentIdentifier.of(currentExtensionId(), slotKey));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DocumentVersionListResponse> listDocumentVersions(String slotKey) {
        List<DocumentVersionResponse> versions = documentService.listVersions(DocumentIdentifier.of(currentExtensionId(), slotKey)).stream()
                .map(documentMapper::toVersionResponse)
                .toList();

        return ResponseEntity.ok(new DocumentVersionListResponse().versions(versions));
    }

    @Override
    public ResponseEntity<DocumentResponse> restoreDocumentVersion(String slotKey, UUID versionId) {
        DocumentData restored = documentService.restoreVersion(DocumentIdentifier.of(currentExtensionId(), slotKey), versionId);
        return ResponseEntity.ok(documentMapper.toResponse(restored, slotKey));
    }

    private UUID currentExtensionId() {
        ApiKeyPrincipal principal = Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(p -> (ApiKeyPrincipal) p)
                .orElseThrow();

        return principal.extensionId();
    }
}