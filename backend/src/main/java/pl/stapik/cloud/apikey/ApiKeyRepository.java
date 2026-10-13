package pl.stapik.cloud.apikey;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.dto.ApiKeyActivity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyData, UUID> {
    Optional<ApiKeyData> findByKeyPrefixAndRevokedFalse(String keyPrefix);
    List<ApiKeyData> findByExtensionId(UUID extensionId);

    @Query("""
            SELECT apiKey.id AS id,
                   apiKey.label AS label,
                   apiKey.keyPrefix AS keyPrefix,
                   apiKey.scope AS scope,
                   apiKey.lastUsedAt AS lastUsedAt,
                   extension.id AS extensionId,
                   extension.displayName AS extensionName
            FROM ApiKeyData apiKey, ExtensionData extension
            WHERE apiKey.extensionId = extension.id
              AND apiKey.revoked = false
            ORDER BY apiKey.lastUsedAt DESC NULLS LAST, apiKey.createdAt DESC
            """)
    List<ApiKeyActivity> findMostRecentlyUsed(Pageable pageable);
}
