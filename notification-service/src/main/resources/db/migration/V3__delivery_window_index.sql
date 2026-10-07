-- Bounded recent-delivery reporting joins back to the immutable source event.
CREATE INDEX IF NOT EXISTS idx_notification_sent_window
    ON notifications(sent_at, source_event_id) WHERE status = 'SENT';
