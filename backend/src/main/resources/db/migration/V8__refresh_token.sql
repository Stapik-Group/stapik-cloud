CREATE TABLE refresh_token (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id  UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ
);

CREATE INDEX idx_refresh_token_admin_user ON refresh_token(admin_user_id);
