-- Apply this migration when the deployment does not allow Hibernate to update the schema.
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_status VARCHAR(24);
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_storage_key VARCHAR(1024);
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_content_type VARCHAR(255);
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_size_bytes BIGINT;
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_width INTEGER;
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_height INTEGER;
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_error VARCHAR(512);
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_attempts INTEGER;
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS preview_updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_attachment_preview_recovery
    ON attachments (preview_status, created_at)
    WHERE deleted = FALSE;
