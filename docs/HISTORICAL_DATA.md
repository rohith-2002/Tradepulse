# Historical market simulation

The demo profile uses recorded daily OHLCV for RELIANCE, TCS, INFY, HDFCBANK, ICICIBANK and ITC. The starting history is **1–31 March 2020**, with 21 trading sessions per instrument. **April 2020**, with 18 sessions per instrument, is the subsequent moving-market continuation, revealed one daily candle at a time. “Future” means after the simulation's March starting point; these are observed historical prices, not predictions of actual future markets. This is a deliberately volatile historical stress test, not the latest month or a live quotation service.

## Source and reproducibility

Public source: [Ram9219 NSE historical archive](https://github.com/Ram9219/NIFTY-50-Stock-Market-Data-2000---2022-/tree/da4ed35f2474b2f9fafd85ccc15877b178f6ce69). The bundled `tradepulse/src/main/resources/market/history.json` records the exact revision, per-file URL, download timestamp and SHA-256 hash of each downloaded CSV. It works offline at application startup. No API key or external database credentials are required for the dataset.

Run `./scripts/import-market-history.ps1` from PowerShell to reproduce the snapshot from the pinned source. The script checks positive OHLC, high/low bounds, nonnegative volume, unique dates and sufficient history before writing the snapshot. Downloads are cached in `.market-downloads`. Failed downloads stop the import without replacing the bundled snapshot. Rebuild/restart the backend after updating resources.

This is publicly accessible historical data; the archive does not state a redistribution license or its corporate-action adjustment methodology. Do not describe it as a verified open-license exchange feed. OHLC columns are retained as published, rounded to four decimal places; the separate `Adj Close` column is not used. Historical prices may differ from current corporate-action-adjusted quotes. The data has structural validation, not independent exchange reconciliation.

## Volatility and replay

| Stock | Daily volatility in the selected month |
|---|---:|
| RELIANCE | 7.26% |
| ICICIBANK | 6.68% |
| ITC | 6.03% |
| HDFCBANK | 5.83% |
| INFY | 5.59% |
| TCS | 4.49% |

Volatility is the sample standard deviation of log returns between consecutive closes within March, multiplied by 100. It is not annualized. The dashboard sorts these six instruments by this measure; this is not a claim about the most volatile stocks across the exchange. The month was chosen as a stress scenario, not as an unbiased performance sample.

The initial price is March 31's recorded close. Every three seconds the demo feed publishes the next April day's recorded OHLC, close and daily volume. After April's final trading day, it loops back to April's start; the dashboard discards the prior cycle's April candles and retains March. `timestamp` is the delivery time; `sourceDate` is the actual historical trading date, and `feedMode` is `HISTORICAL_REPLAY`. Percentage change is relative to the preceding recorded trading day's close, including March 31 for April's first session. This is accelerated daily replay, not reconstructed intraday trades. Day volume must not be interpreted as three seconds of trading volume. Alerts continue to be one-shot and can trigger during replay; existing active alerts retain their thresholds.

`GET /api/market/quote/{symbol}` returns the replay's current recorded price directly, without waiting for Kafka consumption. The dashboard loads historical candles independently, immediately falls back to their latest close, receives WebSocket updates and polls this endpoint every three seconds. A metadata/tick request failure no longer suppresses a valid historical price; truly missing data displays an explicit dash and status. Historical candle responses include only March and April days already revealed by this running replay. The replay is shared by all connected users and resets on backend restart.

## Backtests and existing databases

The snapshot contains 83 earlier sessions from November 2019 through February 2020 for indicator warm-up. The dashboard starts with March and appends revealed April sessions. Backtests default to March: the worker explicitly excludes candles after the requested end date, so April cannot leak into March's results. Users can explicitly select April to evaluate that later period. Backtests fetch earlier bars to initialize indicators but do not trade or report equity before the requested start date. Default SMA, RSI and MACD parameters work over March; excessive custom periods fail with an explicit warm-up error. Trades still execute at the following bar's open with fees and final-close liquidation. No synthetic candles, weekend candles or forward-filled gaps are added.

On demo startup, the importer replaces the old seeder's bars for symbols marked `sector=Demo`, then upserts the sourced daily candles. Repeat startup is idempotent. Accounts, alerts, strategies and previous backtest results remain; previous results are not recalculated. Old generated ticks remain stored for audit but are filtered from the demo market API. Flyway migration V5 stores replay provenance and percentage changes on new ticks. Other intervals are not seeded by this dataset.

Start infrastructure, rebuild the root Maven reactor (both modules changed), and restart with the demo profile. Rebuild Angular or restart its development server. For containers use `docker compose up -d --build` once Docker infrastructure is working.

After a successful root build, stop the previous backend process and start the packaged application from `D:\tradepulse`:

```powershell
java -jar .\tradepulse\target\tradepulse-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

Keep the existing PostgreSQL, Redis and Kafka containers running. Refresh the Angular page; if using a built container UI, rebuild its image. The Java server must be restarted to load the new quote endpoint, event fields and migration. Refreshing only the browser cannot update an older backend process.

## Interview points

- Explain the separation of event time (historical date) and processing time (Kafka delivery timestamp).
- Explain reproducible inputs: pinned revision, raw-file hashes, validation and offline snapshot.
- Explain why indicator warm-up must precede the evaluation window without allowing earlier trades.
- Explain why a daily OHLC candle cannot reveal the intraday path or the order in which its high and low occurred.
- Explain selection bias: a volatile month tests behavior under stress but cannot establish expected live profitability.
- Explain remaining modeling limits: unknown source adjustment policy, no slippage, taxes, order-book liquidity or intraday fills, and no independent exchange reconciliation.
