ALTER TABLE document ADD COLUMN partition_key VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE document ADD COLUMN size_bytes BIGINT NOT NULL DEFAULT 0;

UPDATE document SET size_bytes = octet_length(content);

ALTER TABLE document DROP CONSTRAINT uq_document_slot;
ALTER TABLE document ADD CONSTRAINT uq_document_slot_partition UNIQUE (document_slot_id, partition_key);
