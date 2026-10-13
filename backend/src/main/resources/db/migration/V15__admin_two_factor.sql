ALTER TABLE admin_user ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE admin_user ADD COLUMN totp_last_used_step BIGINT;

CREATE TABLE admin_backup_code (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id  UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    code_hash      VARCHAR(255) NOT NULL,
    used_at        TIMESTAMPTZ
);

CREATE INDEX idx_admin_backup_code_admin_user ON admin_backup_code(admin_user_id);
