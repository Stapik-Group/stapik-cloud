package pl.stapik.cloud.document;

import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.dto.DocumentIdentifier;
import pl.stapik.cloud.document.dto.WriteResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentData getCurrent(DocumentIdentifier identifier);
    List<DocumentVersionData> listVersions(DocumentIdentifier identifier);
    WriteResult write(DocumentIdentifier identifier, String content, Instant clientLastKnownUpdate);
    DocumentData restoreVersion(DocumentIdentifier identifier, UUID versionId);
    void delete(DocumentIdentifier identifier);
}
