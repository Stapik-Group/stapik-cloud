CREATE TABLE document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_slot_id    UUID NOT NULL REFERENCES document_slot(id) ON DELETE CASCADE,
    content             TEXT NOT NULL,
    content_hash        VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_key_id   UUID REFERENCES api_key(id) ON DELETE SET NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_document_slot UNIQUE (document_slot_id)
);

CREATE TABLE document_version (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    content      TEXT NOT NULL,
    saved_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason       VARCHAR(30) NOT NULL CHECK (reason IN ('NORMAL_WRITE', 'CONFLICT_DISCARDED', 'MANUAL_RESTORE'))
);

CREATE INDEX idx_document_version_document ON document_version(document_id, saved_at DESC);