package pl.stapik.cloud.apikey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ApiKeyRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldFindByKeyPrefixAndRevokedFalse() {
        // given
        ExtensionData extension = createExtension("test-ext");
        String prefix = "test-pre";

        createKey(extension.getId(), prefix, "hash1", false);
        createKey(extension.getId(), "other-pre", "hash2", false);
        createKey(extension.getId(), "revoked-pre", "hash3", true);

        // when
        Optional<ApiKeyData> result = apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getKeyPrefix()).isEqualTo(prefix);
        assertThat(result.get().isRevoked()).isFalse();
    }

    @Test
    void shouldFindAllKeysByExtensionId() {
        // given
        ExtensionData extension = createExtension("test-ext");
        ExtensionData otherExtension = createExtension("other-test-ext");

        createKey(extension.getId(), "pre1", "hash1", false);
        createKey(extension.getId(), "pre2", "hash2", false);
        createKey(otherExtension.getId(), "pre3", "hash3", false);

        // when
        List<ApiKeyData> result = apiKeyRepository.findByExtensionId(extension.getId());

        // then
        assertThat(result)
                .hasSize(2)
                .allMatch(key -> key.getExtensionId().equals(extension.getId()));
    }

    private void createKey(UUID extensionId, String prefix, String hash, boolean revoked) {
        ApiKeyData key = new ApiKeyData();
        key.setExtensionId(extensionId);
        key.setKeyPrefix(prefix);
        key.setHashedKey(hash);
        key.setScope(ApiKeyScope.READ_ONLY);
        key.setRevoked(revoked);
        key.setCreatedAt(Instant.now());
        key.setLabel("test-key");
        apiKeyRepository.save(key);
    }

    private ExtensionData createExtension(String slug) {
        ExtensionData ext = new ExtensionData();
        ext.setSlug(slug);
        ext.setDisplayName("Test Extension");
        ext.setEnabled(true);
        ext.setCreatedAt(Instant.now());
        return extensionRepository.save(ext);
    }
}