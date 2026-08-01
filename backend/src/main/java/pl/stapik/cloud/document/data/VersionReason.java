package pl.stapik.cloud.document.data;

public enum VersionReason {
    NORMAL_WRITE,
    CONFLICT_DISCARDED,
    MANUAL_RESTORE
}