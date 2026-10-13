ALTER TABLE refresh_token ADD COLUMN session_id UUID;
ALTER TABLE refresh_token ADD COLUMN session_started_at TIMESTAMPTZ;
ALTER TABLE refresh_token ADD COLUMN ip_address VARCHAR(64);
ALTER TABLE refresh_token ADD COLUMN user_agent VARCHAR(512);

UPDATE refresh_token SET session_id = id, session_started_at = created_at;

ALTER TABLE refresh_token ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE refresh_token ALTER COLUMN session_started_at SET NOT NULL;

CREATE INDEX idx_refresh_token_session ON refresh_token(session_id);
