CREATE TABLE admin_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    totp_secret    VARCHAR(255),
    role           VARCHAR(20) NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER', 'VIEWER')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);