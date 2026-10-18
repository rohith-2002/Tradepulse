ALTER TABLE market_ticks ADD COLUMN source_date DATE;
ALTER TABLE market_ticks ADD COLUMN feed_mode VARCHAR(255);
ALTER TABLE market_ticks ADD COLUMN change_percent NUMERIC(18,4);
