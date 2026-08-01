CREATE TABLE extension (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug         VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    icon_glyph   VARCHAR(50),
    color        VARCHAR(20),
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_slot (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extension_id           UUID NOT NULL REFERENCES extension(id) ON DELETE CASCADE,
    slot_key               VARCHAR(100) NOT NULL,
    content_type           VARCHAR(30) NOT NULL CHECK (content_type IN ('JSON', 'TEXT', 'BINARY', 'BINARY_COLLECTION')),
    filename_pattern       VARCHAR(200),
    max_size_bytes         BIGINT NOT NULL DEFAULT 1048576,
    versioning_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    max_versions_retained  INT NOT NULL DEFAULT 20,
    conflict_strategy      VARCHAR(40) NOT NULL DEFAULT 'LAST_WRITE_WINS_WITH_SHADOW_COPY' CHECK (conflict_strategy IN ('LAST_WRITE_WINS', 'LAST_WRITE_WINS_WITH_SHADOW_COPY')),
    encryption_required    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_slot_key UNIQUE (extension_id, slot_key)
);

CREATE INDEX idx_document_slot_extension ON document_slot(extension_id);

CREATE TABLE api_key (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extension_id    UUID NOT NULL REFERENCES extension(id) ON DELETE CASCADE,
    label           VARCHAR(100) NOT NULL,
    key_prefix      VARCHAR(12) NOT NULL,
    hashed_key      VARCHAR(255) NOT NULL,
    scope           VARCHAR(20) NOT NULL CHECK (scope IN ('READ_ONLY', 'READ_WRITE')),
    ip_allowlist    VARCHAR(100),
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_key_extension ON api_key(extension_id);
CREATE INDEX idx_api_key_prefix ON api_key(key_prefix) WHERE revoked = FALSE;