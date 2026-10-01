ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS source_event_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_source_event_channel
    ON notifications(source_event_id, channel)
    WHERE source_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notification_source_event
    ON notifications(source_event_id);
