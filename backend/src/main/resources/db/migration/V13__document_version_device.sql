ALTER TABLE document_version ADD COLUMN device_id UUID REFERENCES api_key(id) ON DELETE SET NULL;
ALTER TABLE document_version ADD COLUMN device_label VARCHAR(255);
