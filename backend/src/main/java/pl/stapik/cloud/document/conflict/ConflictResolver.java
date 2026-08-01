package pl.stapik.cloud.document.conflict;

import pl.stapik.cloud.documentslot.data.ConflictStrategy;

import java.time.Instant;

public interface ConflictResolver {
    ConflictStrategy supports();
    ConflictDecision resolve(Instant existingUpdatedAt, Instant clientLastKnownUpdate);
}