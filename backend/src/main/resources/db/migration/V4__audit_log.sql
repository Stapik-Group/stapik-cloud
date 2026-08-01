CREATE TABLE audit_log_entry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extension_id  UUID REFERENCES extension(id) ON DELETE SET NULL,
    actor         VARCHAR(100) NOT NULL,
    action        VARCHAR(100) NOT NULL,
    details       TEXT,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log_entry(occurred_at DESC);
CREATE INDEX idx_audit_log_extension ON audit_log_entry(extension_id);