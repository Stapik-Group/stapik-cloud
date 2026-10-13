CREATE TABLE asset_upload (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_slot_id           UUID NOT NULL REFERENCES document_slot(id) ON DELETE CASCADE,
    filename                   VARCHAR(255) NOT NULL,
    mime_type                  VARCHAR(100) NOT NULL,
    size_bytes                 BIGINT NOT NULL,
    received_bytes             BIGINT NOT NULL DEFAULT 0,
    expected_checksum_sha256   VARCHAR(64),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at                 TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_asset_upload_expires_at ON asset_upload(expires_at);
