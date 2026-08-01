CREATE TABLE asset (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_slot_id    UUID NOT NULL REFERENCES document_slot(id) ON DELETE CASCADE,
    filename            VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(100) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    checksum_sha256     VARCHAR(64) NOT NULL,
    updated_by_key_id   UUID REFERENCES api_key(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_asset_filename UNIQUE (document_slot_id, filename)
);

CREATE INDEX idx_asset_slot ON asset(document_slot_id);