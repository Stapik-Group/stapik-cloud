package pl.stapik.cloud.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.common.crypto.ContentEncryptor;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class DocumentEncryptionRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 50;
    private static final List<String> CONTENT_TABLES = List.of("document", "document_version");

    private record StoredContent(UUID id, String content) { }

    private final JdbcTemplate jdbcTemplate;
    private final ContentEncryptor contentEncryptor;

    public DocumentEncryptionRunner(DataSource dataSource, ContentEncryptor contentEncryptor) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.contentEncryptor = contentEncryptor;
    }

    @Override
    public void run(ApplicationArguments args) {
        CONTENT_TABLES.forEach(this::encryptContent);
    }

    private void encryptContent(String table) {
        String upToDatePattern = contentEncryptor.currentPrefix() + "%";
        int updated = 0;

        List<StoredContent> outdated = findOutdated(table, upToDatePattern);
        while (!outdated.isEmpty()) {
            outdated.forEach(row -> jdbcTemplate.update(
                    "UPDATE " + table + " SET content = ? WHERE id = ?", contentEncryptor.rewrap(row.content()), row.id()));
            updated += outdated.size();
            outdated = findOutdated(table, upToDatePattern);
        }

        if (updated > 0) {
            log.info("Encrypted {} rows of {} with the active key", updated, table);
        }
    }

    private List<StoredContent> findOutdated(String table, String upToDatePattern) {
        return jdbcTemplate.query(
                "SELECT id, content FROM " + table + " WHERE content NOT LIKE ? ORDER BY id LIMIT " + BATCH_SIZE,
                (resultSet, rowNumber) -> new StoredContent(resultSet.getObject("id", UUID.class), resultSet.getString("content")),
                upToDatePattern);
    }
}
