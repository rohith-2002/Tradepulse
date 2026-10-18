CREATE UNIQUE INDEX uq_tick_symbol_timestamp ON market_ticks(symbol_id, ts);
CREATE INDEX idx_alerts_symbol_status ON alerts(symbol_id, status);
CREATE INDEX idx_runs_strategy ON backtest_runs(strategy_id);
