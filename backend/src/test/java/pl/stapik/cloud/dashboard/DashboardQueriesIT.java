package pl.stapik.cloud.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.apikey.ApiKeyRepository;
import pl.stapik.cloud.apikey.data.ApiKeyData;
import pl.stapik.cloud.apikey.data.ApiKeyScope;
import pl.stapik.cloud.apikey.dto.ApiKeyActivity;
import pl.stapik.cloud.asset.AssetRepository;
import pl.stapik.cloud.asset.data.AssetData;
import pl.stapik.cloud.document.DocumentRepository;
import pl.stapik.cloud.document.DocumentVersionRepository;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.document.dto.DailyWriteTotal;
import pl.stapik.cloud.document.dto.DocumentConflictSummary;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Transactional
class DashboardQueriesIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Test
    void shouldSumAndCountOnlyActiveDocuments() {
        // given
        long sizeBefore = documentRepository.sumActiveSizeBytes();
        long countBefore = documentRepository.countByDeletedAtIsNull();
        UUID slotId = createSlot(createExtension("sum-ext")).getId();
        createDocument(slotId, "", 100, null);
        createDocument(slotId, "2026", 250, null);
        createDocument(slotId, "2025", 999, Instant.now());

        // when & then
        assertThat(documentRepository.sumActiveSizeBytes() - sizeBefore).isEqualTo(350);
        assertThat(documentRepository.countByDeletedAtIsNull() - countBefore).isEqualTo(2);
    }

    @Test
    void shouldSumAssetSizes() {
        // given
        long sizeBefore = assetRepository.sumSizeBytes();
        UUID slotId = createSlot(createExtension("asset-ext")).getId();
        createAsset(slotId, "a.png", 1000);
        createAsset(slotId, "b.png", 500);

        // when & then
        assertThat(assetRepository.sumSizeBytes() - sizeBefore).isEqualTo(1500);
    }

    @Test
    void shouldListActiveKeysMostRecentlyUsedFirstWithNeverUsedLast() {
        // given
        ExtensionData extension = createExtension("keys-ext");
        Instant now = Instant.now();
        createKey(extension.getId(), "never", "k-never", null, false);
        createKey(extension.getId(), "older", "k-older", now.minusSeconds(600), false);
        createKey(extension.getId(), "newer", "k-newer", now.minusSeconds(10), false);
        createKey(extension.getId(), "revoked", "k-revoked", now, true);

        // when
        List<ApiKeyActivity> activity = apiKeyRepository.findMostRecentlyUsed(PageRequest.ofSize(10));

        // then
        assertThat(activity)
                .extracting(ApiKeyActivity::getLabel)
                .containsExactly("newer", "older", "never");
        assertThat(activity.getFirst().getExtensionName()).isEqualTo("Extension keys-ext");
    }

    @Test
    void shouldListOnlyDiscardedVersionsNewestFirst() {
        // given
        ExtensionData extension = createExtension("conflict-ext");
        DocumentSlotData slot = createSlot(extension);
        UUID documentId = createDocument(slot.getId(), "2026", 10, null);
        Instant now = Instant.now();
        createVersion(documentId, VersionReason.CONFLICT_DISCARDED, now.minusSeconds(60), "old device");
        createVersion(documentId, VersionReason.NORMAL_WRITE, now.minusSeconds(30), null);
        createVersion(documentId, VersionReason.CONFLICT_DISCARDED, now.minusSeconds(5), "new device");

        // when
        List<DocumentConflictSummary> conflicts = documentVersionRepository
                .findRecentByReason(VersionReason.CONFLICT_DISCARDED, PageRequest.ofSize(10));

        // then
        assertThat(conflicts)
                .extracting(DocumentConflictSummary::getDeviceLabel)
                .containsExactly("new device", "old device");
        assertThat(conflicts.getFirst().getSlotKey()).isEqualTo(slot.getSlotKey());
        assertThat(conflicts.getFirst().getPartitionKey()).isEqualTo("2026");
        assertThat(conflicts.getFirst().getExtensionId()).isEqualTo(extension.getId());
    }

    @Test
    void shouldCountNormalWritesPerUtcDayWithinWindow() {
        // given
        UUID documentId = createDocument(createSlot(createExtension("writes-ext")).getId(), "", 10, null);
        Instant since = Instant.parse("2031-03-01T00:00:00Z");
        createVersion(documentId, VersionReason.NORMAL_WRITE, Instant.parse("2031-02-28T23:59:59Z"), null);
        createVersion(documentId, VersionReason.NORMAL_WRITE, Instant.parse("2031-03-01T00:00:00Z"), null);
        createVersion(documentId, VersionReason.NORMAL_WRITE, Instant.parse("2031-03-01T23:59:59Z"), null);
        createVersion(documentId, VersionReason.CONFLICT_DISCARDED, Instant.parse("2031-03-01T12:00:00Z"), null);
        createVersion(documentId, VersionReason.NORMAL_WRITE, Instant.parse("2031-03-03T08:00:00Z"), null);

        // when
        List<DailyWriteTotal> totals = documentVersionRepository.countWritesPerDaySince(since);

        // then
        assertThat(totals)
                .extracting(DailyWriteTotal::getDay, DailyWriteTotal::getTotal)
                .containsExactly(
                        tuple("2031-03-01", 2L),
                        tuple("2031-03-03", 1L)
                );
    }

    private ExtensionData createExtension(String slug) {
        return extensionRepository.save(ExtensionData.builder()
                .slug(slug)
                .displayName("Extension " + slug)
                .createdAt(Instant.now())
                .build());
    }

    private DocumentSlotData createSlot(ExtensionData extension) {
        return documentSlotRepository.save(DocumentSlotData.builder()
                .extensionId(extension.getId())
                .slotKey("slot-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .maxSizeBytes(1_048_576L)
                .maxVersionsRetained(20)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS_WITH_SHADOW_COPY)
                .createdAt(Instant.now())
                .build());
    }

    private UUID createDocument(UUID slotId, String partitionKey, long sizeBytes, Instant deletedAt) {
        return documentRepository.save(DocumentData.builder()
                .documentSlotId(slotId)
                .partitionKey(partitionKey)
                .content("{}")
                .contentHash("hash")
                .sizeBytes(sizeBytes)
                .updatedAt(Instant.now())
                .deletedAt(deletedAt)
                .build()).getId();
    }

    private void createVersion(UUID documentId, VersionReason reason, Instant savedAt, String deviceLabel) {
        documentVersionRepository.save(DocumentVersionData.builder()
                .documentId(documentId)
                .content("{}")
                .savedAt(savedAt)
                .reason(reason)
                .deviceLabel(deviceLabel)
                .build());
    }

    private void createAsset(UUID slotId, String filename, long sizeBytes) {
        assetRepository.save(AssetData.builder()
                .documentSlotId(slotId)
                .filename(filename)
                .mimeType("image/png")
                .sizeBytes(sizeBytes)
                .storagePath("/data/assets/" + filename)
                .checksumSha256("checksum")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void createKey(UUID extensionId, String label, String prefix, Instant lastUsedAt, boolean revoked) {
        apiKeyRepository.save(ApiKeyData.builder()
                .extensionId(extensionId)
                .label(label)
                .keyPrefix(prefix)
                .hashedKey("hash")
                .scope(ApiKeyScope.READ_WRITE)
                .lastUsedAt(lastUsedAt)
                .revoked(revoked)
                .createdAt(Instant.now())
                .build());
    }
}
