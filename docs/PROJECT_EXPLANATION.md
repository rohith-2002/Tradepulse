# TradePulse: short interview explanation

> Runtime update (19 October 2026): the repeating historical replay described below has been replaced by continuous synthetic ticks calibrated from historical data, with forming 1-minute candles and live strategy evaluation. Use [the current simulation notes](CONTINUOUS_SIMULATION.md) for feed behavior and limitations; the outbox, authentication and fixed historical-backtest concepts still apply.

Prepared 19 October 2026. Detailed questions and cross-questions: [interview study material](INTERVIEW_STUDY_MATERIAL.md).

## 30-second introduction

“TradePulse is a full-stack market research application built with Angular and Spring Boot. Users can view historical candles, follow a replay of stock prices, create alerts and backtest SMA, RSI and MACD strategies. PostgreSQL stores application data, Kafka handles event processing, and Redis supports caching. My main focus was reliable processing, secure user access and realistic simulation boundaries. The demo uses recorded historical prices; it does not predict prices or place real trades.”

## 60–90-second introduction

“TradePulse helps users explore market movements and test simple trading strategies in a controlled environment. I built it as an Angular frontend with a Spring Boot modular monolith, organized into authentication, market data, alerts, backtesting and notification modules.

“A recorded market update flows through Kafka, is persisted, evaluated against user alerts and streamed to the UI. When an alert triggers, its state change and an outbox record are saved in the same database transaction. That allows publication to be retried, while stable event IDs prevent duplicate inbox records. JWT authentication and ownership checks protect user data.

“For simulation, March 2020 provides the starting history and April provides the subsequent replay. Earlier candles warm up the indicators. Backtests use previous-bar signals and next-bar-open fills to reduce look-ahead bias, and report return, drawdown and trade statistics. The dashboard also falls back to a recorded closing price if streaming is unavailable.

“The project demonstrates reliable event processing and full-stack integration, while keeping its limits explicit: it has no broker order execution, and its daily-bar model does not include realistic intraday fills, slippage or taxes.”

## Architecture in one minute

```text
                        Angular dashboard
                     REST + authenticated STOMP
                               |
                      Spring Boot backend
                 authentication and ownership checks
                               |
      +------------------------+------------------------+
      |                        |                        |
 Market data / replay      User alerts             Backtest jobs
      |                        |                        |
 Kafka market events      State + outbox           Async engine
      |                   committed together       Result + outbox
 Persist / evaluate            |                        |
      |                   Kafka notification flow      |
 Stream to browser        Deduplicated inbox       Result polling

 PostgreSQL: durable state and constraints
 Redis: cache for non-replay history; failures fall back to PostgreSQL
 REST quote endpoint: current replay quote or recorded-price fallback
```

## Five points to emphasize

1. **Reliability:** Transactional outbox with duplicate-aware consumers; do not claim exactly-once email delivery.
2. **Security:** Password hashing, short-lived JWTs, refresh rotation, ownership checks and restricted STOMP subscriptions.
3. **Simulation correctness:** Recorded data, separate warm-up history, bounded evaluation dates and next-bar execution.
4. **UI resilience:** A valid historical price remains visible when another request or the stream fails.
5. **Honest limitations:** The backend is a modular monolith, the local Kafka deployment has one replica, and scaling WebSocket delivery requires additional architecture.

## If asked about your hardest challenge

“A challenging part was reasoning across boundaries. An alert update in PostgreSQL and an event publication to Kafka cannot be treated as one ordinary database transaction. I used an outbox so the publication intent commits with the alert, then made downstream inbox handling duplicate-aware. The important lesson was to explain each guarantee separately: retryable publication, idempotent persistence, and external side effects such as email that can still repeat.”

Alternative UI example: “The price display depended on multiple requests finishing together. I separated the loading paths, displayed the latest recorded close immediately, and added quote polling alongside WebSockets. That preserved a useful screen even when streaming was unavailable.”

Use whichever story you can explain from the code; do not claim benchmarks or production incidents that were never measured or experienced.

## Verification you can state accurately

- The recorded full backend run passed 17 tests; Angular passed nine tests and its production build.
- A later targeted run passed two new signing-key tests plus the application-context test. That was not a fresh full-suite run of all 19 backend tests.
- Full PostgreSQL deployment acceptance, external SMTP delivery and a complete visual-browser walkthrough were not established by those checks.

## One-line conclusion

“TradePulse shows how to connect a secure Angular interface, asynchronous Java services and reliable event processing around a reproducible historical market simulation.”
