package pl.stapik.cloud.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.stapik.cloud.apikey.data.ApiKeyData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyData, UUID> {
    Optional<ApiKeyData> findByKeyPrefixAndRevokedFalse(String keyPrefix);
    List<ApiKeyData> findByExtensionId(UUID extensionId);
}
