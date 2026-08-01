package pl.stapik.cloud.apikey.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "extension_id", nullable = false)
    private UUID extensionId;

    @Column(nullable = false)
    private String label;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "hashed_key", nullable = false)
    private String hashedKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyScope scope;

    @Column(name = "ip_allowlist")
    private String ipAllowlist;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
