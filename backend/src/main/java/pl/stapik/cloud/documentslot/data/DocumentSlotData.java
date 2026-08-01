package pl.stapik.cloud.documentslot.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_slot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSlotData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "extension_id", nullable = false)
    private UUID extensionId;

    @Column(name = "slot_key", nullable = false)
    private String slotKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "filename_pattern")
    private String filenamePattern;

    @Column(name = "max_size_bytes", nullable = false)
    private long maxSizeBytes;

    @Column(name = "versioning_enabled", nullable = false)
    private boolean versioningEnabled;

    @Column(name = "max_versions_retained", nullable = false)
    private int maxVersionsRetained;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_strategy", nullable = false)
    private ConflictStrategy conflictStrategy;

    @Column(name = "encryption_required", nullable = false)
    private boolean encryptionRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
