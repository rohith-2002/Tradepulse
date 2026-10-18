ALTER TABLE alerts ADD COLUMN notification_channel VARCHAR(255) NOT NULL DEFAULT 'IN_APP';
ALTER TABLE alerts ADD CONSTRAINT ck_alert_channel CHECK (notification_channel IN ('IN_APP','WEBSOCKET','EMAIL'));
