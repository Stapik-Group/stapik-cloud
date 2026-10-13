package pl.stapik.cloud.document.conflict.impl;

import org.springframework.stereotype.Component;
import pl.stapik.cloud.document.conflict.ConflictDecision;
import pl.stapik.cloud.document.conflict.ConflictResolver;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;

@Component
public class ShadowCopyResolver implements ConflictResolver {

    @Override
    public ConflictStrategy supports() {
        return ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY;
    }

    @Override
    public ConflictDecision resolve(String existingContentHash, String clientContentHash) {
        boolean clientWasUpToDate = existingContentHash.equals(clientContentHash);
        return clientWasUpToDate ? ConflictDecision.accept() : new ConflictDecision(false, true);
    }
}