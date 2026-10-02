package pl.stapik.cloud.document.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentData {
    public static final String DEFAULT_PARTITION_KEY = "";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_slot_id", nullable = false)
    private UUID documentSlotId;

    @Column(name = "partition_key", nullable = false)
    @Builder.Default
    private String partitionKey = DEFAULT_PARTITION_KEY;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_key_id")
    private UUID updatedByKeyId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
