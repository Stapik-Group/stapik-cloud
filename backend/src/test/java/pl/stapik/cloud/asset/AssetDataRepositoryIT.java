package pl.stapik.cloud.asset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AssetDataRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldFindByDocumentSlotId() {
        // given
        DocumentSlotData slot = createSlot("slot-1");
        createAsset(slot.getId(), "file1.txt", "hash1");
        createAsset(slot.getId(), "file2.txt", "hash2");

        DocumentSlotData otherSlot = createSlot("slot-2");
        createAsset(otherSlot.getId(), "file3.txt", "hash3");

        // when
        List<AssetData> result = assetRepository.findByDocumentSlotId(slot.getId());

        // then
        assertThat(result)
                .hasSize(2)
                .allMatch(asset -> asset.getDocumentSlotId().equals(slot.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenSlotHasNoAssets() {
        // given
        DocumentSlotData slot = createSlot("empty-slot");

        // when
        List<AssetData> result = assetRepository.findByDocumentSlotId(slot.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByDocumentSlotIdAndFilename() {
        // given
        DocumentSlotData slot = createSlot("slot-1");
        createAsset(slot.getId(), "target.txt", "hash-target");
        createAsset(slot.getId(), "other.txt", "hash-other");

        // when
        Optional<AssetData> result = assetRepository.findByDocumentSlotIdAndFilename(slot.getId(), "target.txt");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getFilename()).isEqualTo("target.txt");
        assertThat(result.get().getChecksumSha256()).isEqualTo("hash-target");
    }

    @Test
    void shouldReturnEmptyWhenFilenameNotFoundInSlot() {
        // given
        DocumentSlotData slot = createSlot("slot-1");
        createAsset(slot.getId(), "existing.txt", "hash1");

        // when
        Optional<AssetData> result = assetRepository.findByDocumentSlotIdAndFilename(slot.getId(), "missing.txt");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFindFilenameBelongingToDifferentSlot() {
        // given
        DocumentSlotData slot = createSlot("slot-1");
        DocumentSlotData otherSlot = createSlot("slot-2");
        createAsset(otherSlot.getId(), "shared-name.txt", "hash1");

        // when
        Optional<AssetData> result = assetRepository.findByDocumentSlotIdAndFilename(slot.getId(), "shared-name.txt");

        // then
        assertThat(result).isEmpty();
    }

    private void createAsset(UUID documentSlotId, String filename, String checksum) {
        AssetData assetData = AssetData.builder()
                .documentSlotId(documentSlotId)
                .filename(filename)
                .mimeType("text/plain")
                .sizeBytes(1024L)
                .storagePath("/data/assets/" + filename)
                .checksumSha256(checksum)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        assetRepository.save(assetData);
    }

    private DocumentSlotData createSlot(String slotKey) {
        ExtensionData extension = createExtension("ext-" + UUID.randomUUID());
        DocumentSlotData slot = DocumentSlotData.builder()
                .extensionId(extension.getId())
                .slotKey(slotKey)
                .contentType(ContentType.BINARY)
                .maxSizeBytes(1_048_576L)
                .versioningEnabled(true)
                .maxVersionsRetained(20)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .encryptionRequired(false)
                .createdAt(Instant.now())
                .build();
        return documentSlotRepository.save(slot);
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