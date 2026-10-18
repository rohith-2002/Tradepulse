CREATE TABLE event_outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);
CREATE INDEX idx_outbox_pending ON event_outbox(id) WHERE published_at IS NULL;
ALTER TABLE notifications ADD COLUMN event_id VARCHAR(255) UNIQUE;
