package pl.stapik.cloud.document;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.stapik.cloud.AbstractIntegrationTest;
import pl.stapik.cloud.document.data.DocumentData;
import pl.stapik.cloud.document.data.DocumentVersionData;
import pl.stapik.cloud.document.data.VersionReason;
import pl.stapik.cloud.documentslot.DocumentSlotRepository;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.ContentType;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;
import pl.stapik.cloud.extension.ExtensionData;
import pl.stapik.cloud.extension.ExtensionRepository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEncryptionRunnerIT extends AbstractIntegrationTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentSlotRepository documentSlotRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Autowired
    private DocumentEncryptionRunner runner;

    private JdbcTemplate jdbcTemplate;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        deleteAll();

        ExtensionData extension = extensionRepository.save(ExtensionData.builder()
                .slug("encrypted-ext")
                .displayName("Extension")
                .createdAt(Instant.now())
                .build());
        slotId = documentSlotRepository.save(DocumentSlotData.builder()
                .extensionId(extension.getId())
                .slotKey("slot-key")
                .contentType(ContentType.JSON)
                .conflictStrategy(ConflictStrategy.LAST_WRITE_WINS)
                .createdAt(Instant.now())
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        deleteAll();
    }

    @Test
    void shouldStoreDocumentAndVersionContentEncrypted() {
        // given
        DocumentData saved = documentRepository.save(DocumentData.builder()
                .documentSlotId(slotId)
                .content("top secret document")
                .contentHash("hash")
                .updatedAt(UPDATED_AT)
                .build());
        DocumentVersionData version = documentVersionRepository.save(DocumentVersionData.builder()
                .documentId(saved.getId())
                .content("top secret version")
                .savedAt(UPDATED_AT)
                .reason(VersionReason.NORMAL_WRITE)
                .build());

        // when
        String rawDocument = rawContent("document", saved.getId());
        String rawVersion = rawContent("document_version", version.getId());

        // then
        assertThat(rawDocument).startsWith("enc:v1:").doesNotContain("top secret");
        assertThat(rawVersion).startsWith("enc:v1:").doesNotContain("top secret");
        assertThat(documentRepository.findById(saved.getId())).get()
                .extracting(DocumentData::getContent).isEqualTo("top secret document");
        assertThat(documentVersionRepository.findById(version.getId())).get()
                .extracting(DocumentVersionData::getContent).isEqualTo("top secret version");
    }

    @Test
    void shouldEncryptLegacyPlaintextRowsWithoutChangingTheirMetadata() {
        // given
        UUID documentId = insertPlaintextDocument("legacy document");
        UUID versionId = insertPlaintextVersion(documentId, "legacy version");

        // when
        runner.run(null);

        // then
        assertThat(rawContent("document", documentId)).startsWith("enc:v1:").doesNotContain("legacy");
        assertThat(rawContent("document_version", versionId)).startsWith("enc:v1:").doesNotContain("legacy");
        DocumentData document = documentRepository.findById(documentId).orElseThrow();
        assertThat(document.getContent()).isEqualTo("legacy document");
        assertThat(document.getContentHash()).isEqualTo("legacy-hash");
        assertThat(document.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(documentVersionRepository.findById(versionId).orElseThrow().getContent()).isEqualTo("legacy version");
    }

    @Test
    void shouldLeaveUpToDateRowsUntouched() {
        // given
        DocumentData saved = documentRepository.save(DocumentData.builder()
                .documentSlotId(slotId)
                .content("already encrypted")
                .contentHash("hash")
                .updatedAt(UPDATED_AT)
                .build());
        String before = rawContent("document", saved.getId());

        // when
        runner.run(null);

        // then
        assertThat(rawContent("document", saved.getId())).isEqualTo(before);
    }

    private void deleteAll() {
        jdbcTemplate.execute("DELETE FROM document_version");
        jdbcTemplate.execute("DELETE FROM document");
        jdbcTemplate.execute("DELETE FROM document_slot");
        jdbcTemplate.execute("DELETE FROM extension");
    }

    private String rawContent(String table, UUID id) {
        return jdbcTemplate.queryForObject("SELECT content FROM " + table + " WHERE id = ?", String.class, id);
    }

    private UUID insertPlaintextDocument(String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document (id, document_slot_id, content, content_hash, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, slotId, content, "legacy-hash", Timestamp.from(UPDATED_AT));
        return id;
    }

    private UUID insertPlaintextVersion(UUID documentId, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_version (id, document_id, content, saved_at, reason) VALUES (?, ?, ?, ?, ?)",
                id, documentId, content, Timestamp.from(UPDATED_AT), "NORMAL_WRITE");
        return id;
    }
}
