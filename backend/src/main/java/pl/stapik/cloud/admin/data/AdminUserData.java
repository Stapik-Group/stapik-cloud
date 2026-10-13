package pl.stapik.cloud.admin.data;

import jakarta.persistence.*;
import lombok.*;

import pl.stapik.cloud.common.crypto.EncryptedContentConverter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Convert(converter = EncryptedContentConverter.class)
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    @Column(name = "totp_last_used_step")
    private Long totpLastUsedStep;

    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
