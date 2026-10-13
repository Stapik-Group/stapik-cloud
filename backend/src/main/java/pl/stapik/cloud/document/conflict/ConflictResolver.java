package pl.stapik.cloud.document.conflict;

import pl.stapik.cloud.documentslot.data.ConflictStrategy;

public interface ConflictResolver {
    ConflictStrategy supports();
    ConflictDecision resolve(String existingContentHash, String clientContentHash);
}