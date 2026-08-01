package pl.stapik.cloud.extension;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ExtensionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldReturnTrueWhenSlugExists() {
        // given
        String slug = "unique-slug";
        createExtension(slug);

        // when
        boolean exists = extensionRepository.existsBySlug(slug);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenSlugDoesNotExist() {
        // when
        boolean exists = extensionRepository.existsBySlug("non-existent-slug");

        // then
        assertThat(exists).isFalse();
    }

    private void createExtension(String slug) {
        ExtensionData extension = new ExtensionData();
        extension.setSlug(slug);
        extension.setDisplayName("Test Extension");
        extension.setEnabled(true);
        extension.setCreatedAt(Instant.now());
        extensionRepository.save(extension);
    }
}