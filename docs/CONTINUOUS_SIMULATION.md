# Continuous simulated market

The demo now behaves as a continuously moving market, rather than replaying historical dates. It does not connect to an exchange or place real orders.

## Prices and candles

- Each symbol starts at its last recorded March 2020 close.
- Approximately once per second, the generator samples a centered daily log return from that stock's March data and scales it by the square root of elapsed time divided by a 375-minute session. This preserves a relationship to the observed volatility without applying a whole day's move every second.
- Ticks use current Asia/Kolkata timestamps. They update the current minute's open, high, low, close and cumulative volume. The next minute opens at the previous simulated price; there is no month rollover or automatic price reset.
- The chart initially contains 120 generated warm-up minutes. These are synthetic, not recovered historical intraday observations. The recorded March daily candles remain in a separate history view.
- Simulation runs continuously, including outside exchange hours. Restarting the backend creates a fresh session and signing in again may be necessary.

## Alerts and strategy lab

Alerts evaluate Kafka-delivered simulated ticks. Price conditions use the current generated price; percentage change uses the session's starting price; volume conditions use cumulative volume in the current minute. Alerts remain one-shot, and the alerts page refreshes their status automatically.

The live strategy button captures the selected strategy parameters and evaluates the current simulation session. Generated warm-up minutes seed the indicators without trading. Completed-minute signals execute at the following minute's open. An open position is marked to the latest simulated price, without forced liquidation on every refresh. Profit can remain unchanged while the strategy holds cash or is waiting for a signal. The simulation view refreshes every second and can be paused. It does not create a saved backtest run per tick.

The historical-backtest button remains separate: its date range applies to recorded daily candles, and positions are liquidated at the end of that fixed window. Live strategy periods must fit the 120-minute warm-up; the server reports a validation error if they do not.

## API and operational boundaries

- `GET /api/market/quote/{symbol}`: latest simulated quote, with `feedMode=SIMULATED_LIVE`, `barTime` and `interval=1m`.
- `GET /api/market/ohlcv/{symbol}?interval=1m`: latest 120 generated minute candles, including the forming candle.
- `GET /api/market/ohlcv/{symbol}?interval=1d`: recorded base history.
- `POST /api/backtest/live`: authenticated, read-only calculation with `{name, parameters}`; returns a consistent quote/candle snapshot and live results.

All clients share the backend's current simulation session. Generated minute history is kept in memory for that process session; ticks are also persisted through Kafka. There is no persisted simulation checkpoint or cross-instance leader. Run one demo backend. Kafka must be healthy for alert evaluation and WebSocket delivery; REST quote fallback alone does not prove alert processing succeeded.

This is a statistical demonstration, not a reconstruction of real intraday prices. It does not model an order book, spreads, liquidity, correlations, market hours, slippage or taxes. Volume generation is approximate. Session history grows until restart, and the live strategy recalculates over that session; a long-running production version needs bounded storage/checkpointed indicator and position state.
