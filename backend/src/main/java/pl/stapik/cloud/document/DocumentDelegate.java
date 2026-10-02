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
import pl.stapik.cloud.internal.data.DocumentPartitionListResponse;
import pl.stapik.cloud.internal.data.DocumentPartitionResponse;
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
        return readDocument(DocumentIdentifier.of(currentExtensionId(), slotKey));
    }

    @Override
    public ResponseEntity<DocumentResponse> getDocumentPartition(String slotKey, String partition) {
        return readDocument(DocumentIdentifier.of(currentExtensionId(), slotKey, partition));
    }

    @Override
    public ResponseEntity<DocumentResponse> writeDocument(String slotKey, DocumentWriteRequest documentWriteRequest) {
        return saveDocument(DocumentIdentifier.of(currentExtensionId(), slotKey), documentWriteRequest);
    }

    @Override
    public ResponseEntity<DocumentResponse> writeDocumentPartition(String slotKey, String partition, DocumentWriteRequest documentWriteRequest) {
        return saveDocument(DocumentIdentifier.of(currentExtensionId(), slotKey, partition), documentWriteRequest);
    }

    @Override
    public ResponseEntity<Void> deleteDocument(String slotKey) {
        return removeDocument(DocumentIdentifier.of(currentExtensionId(), slotKey));
    }

    @Override
    public ResponseEntity<Void> deleteDocumentPartition(String slotKey, String partition) {
        return removeDocument(DocumentIdentifier.of(currentExtensionId(), slotKey, partition));
    }

    @Override
    public ResponseEntity<DocumentVersionListResponse> listDocumentVersions(String slotKey) {
        return listVersions(DocumentIdentifier.of(currentExtensionId(), slotKey));
    }

    @Override
    public ResponseEntity<DocumentVersionListResponse> listDocumentPartitionVersions(String slotKey, String partition) {
        return listVersions(DocumentIdentifier.of(currentExtensionId(), slotKey, partition));
    }

    @Override
    public ResponseEntity<DocumentResponse> restoreDocumentVersion(String slotKey, UUID versionId) {
        return restoreVersion(DocumentIdentifier.of(currentExtensionId(), slotKey), versionId);
    }

    @Override
    public ResponseEntity<DocumentResponse> restoreDocumentPartitionVersion(String slotKey, String partition, UUID versionId) {
        return restoreVersion(DocumentIdentifier.of(currentExtensionId(), slotKey, partition), versionId);
    }

    @Override
    public ResponseEntity<DocumentPartitionListResponse> listDocumentPartitions(String slotKey) {
        List<DocumentPartitionResponse> partitions = documentService.listPartitions(DocumentIdentifier.of(currentExtensionId(), slotKey)).stream()
                .map(documentMapper::toPartitionResponse)
                .toList();

        return ResponseEntity.ok(new DocumentPartitionListResponse().partitions(partitions));
    }

    private ResponseEntity<DocumentResponse> readDocument(DocumentIdentifier identifier) {
        DocumentData documentData = documentService.getCurrent(identifier);
        return ResponseEntity.ok(documentMapper.toResponse(documentData, identifier.getSlotKey()));
    }

    private ResponseEntity<DocumentResponse> saveDocument(DocumentIdentifier identifier, DocumentWriteRequest documentWriteRequest) {
        WriteResult result = documentService.write(
                identifier,
                documentWriteRequest.getContent(),
                documentWriteRequest.getClientLastKnownUpdate().toInstant()
        );

        DocumentResponse response = documentMapper.toResponse(result.documentData(), identifier.getSlotKey());
        return result.conflict()
                ? ResponseEntity.status(409).body(response)
                : ResponseEntity.ok(response);
    }

    private ResponseEntity<Void> removeDocument(DocumentIdentifier identifier) {
        documentService.delete(identifier);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<DocumentVersionListResponse> listVersions(DocumentIdentifier identifier) {
        List<DocumentVersionResponse> versions = documentService.listVersions(identifier).stream()
                .map(documentMapper::toVersionResponse)
                .toList();

        return ResponseEntity.ok(new DocumentVersionListResponse().versions(versions));
    }

    private ResponseEntity<DocumentResponse> restoreVersion(DocumentIdentifier identifier, UUID versionId) {
        DocumentData restored = documentService.restoreVersion(identifier, versionId);
        return ResponseEntity.ok(documentMapper.toResponse(restored, identifier.getSlotKey()));
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