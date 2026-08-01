package pl.stapik.cloud.document.conflict;

public record ConflictDecision(boolean writeAccepted, boolean preserveDiscardedVersion) {
    public static ConflictDecision accept() {
        return new ConflictDecision(true, false);
    }
}