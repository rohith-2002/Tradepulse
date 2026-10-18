# TradePulse interview guide

Use the short answers as a starting point, then explain the concrete implementation. Do not claim production trading, a certified live exchange feed, exactly-once email, or distributed WebSocket scaling: those are not implemented or verified here. The demo uses publicly archived historical prices; see [source and replay details](HISTORICAL_DATA.md).

## A 60-second introduction

“TradePulse is a market research platform I developed with an Angular frontend and a Spring Boot modular monolith. It streams prices, evaluates user alerts, sends notifications, and backtests SMA, RSI and MACD strategies. PostgreSQL stores durable business data, Redis accelerates historical reads, and a reusable Kafka library separates event transport from business logic. I secured REST and WebSocket access, added owner-level checks, and used a transactional outbox to avoid losing events between a database commit and Kafka publication. The demo replays recorded daily prices from a volatile historical month, with source provenance and earlier indicator warm-up data, without a live market-data subscription.”

## Architecture to draw on a whiteboard

```text
Angular SPA ── JWT REST ──► Spring Boot modules ──► PostgreSQL
     ▲                          │                   └─ event_outbox
     │                          ├─► Redis cache
     └──── authenticated STOMP ──┤
                                │
Demo/provider ─► tick topic ─► persist tick + evaluate alerts
                                      │
                              alert + outbox commit
                                      │
                             outbox publisher ─► alert topic
                                                      │
                                               notification outbox
                                                      │
                                               notification topic
                                                      │
                                      deduplicated inbox / SMTP / WS
```

The backtest path is REST → ownership/parameter validation → persisted PENDING run → separate asynchronous worker → date-filtered history → engine → result and outbox commit → polling/private result event.

## Questions and strong answers

### 1. Why a modular monolith instead of microservices?

“The domain is small enough that one deployable simplifies operations and transactional consistency. Authentication, market data, alerts, backtesting and notifications have separate packages and services. The Kafka module has transport contracts and handlers without business logic. If one workload needs independent scaling later, that boundary makes extraction easier. Kafka use alone does not make this a microservices architecture.”

### 2. Why Kafka when everything runs in one application?

“It decouples event arrival from downstream processing, buffers temporary delays and allows consumers to retry independently. A symbol key preserves order for that symbol within a topic partition. There is no global ordering across partitions. For a very small deployment, this infrastructure is more complex than an in-process queue; I used it to demonstrate event-driven processing and recovery.”

### 3. How does an alert become a notification?

“A tick is persisted and matched against active alerts for its symbol. Matching alert rows are locked. When a condition is satisfied, the alert changes to TRIGGERED and an outbox record is inserted in the same database transaction. A publisher sends committed outbox records to Kafka. The alert consumer produces a notification request through another outbox record. The notification consumer uses a stable event ID to avoid inserting the same logical notification twice.”

### 4. Why is the transactional outbox necessary?

“A normal database transaction cannot atomically commit to PostgreSQL and Kafka. If I update an alert and then the publish fails, the notification could be lost. The outbox stores the state change and publication intent together. Publishing is retried until confirmed. A crash after publication but before recording completion can repeat an event, so consumers must be idempotent.”

### 5. Is delivery exactly once?

“No. Publication is at least once. Database deduplication and locking make the notification's durable effect idempotent for a stable event ID. External SMTP delivery has an additional crash window and cannot be made exactly once just by adding a database transaction. Kafka producer idempotence and Kafka transactions do not automatically solve a database-plus-email transaction.”

### 6. What happens when a consumer throws an exception?

“The consumer rethrows the exception so Spring Kafka can apply the configured error handler. It retries with a fixed delay and sends exhausted records to a dead-letter topic. The original code swallowed exceptions, which could acknowledge failed processing. DLT records need an operational inspection and replay process; simply having a DLT is not complete incident handling.”

### 7. How do you avoid two requests triggering the same alert?

“The repository locks matching active alerts pessimistically inside a transaction. Once committed, later evaluations no longer see the row as ACTIVE. Kafka symbol partitioning reduces normal contention, but the database protects the state transition. Notification event IDs handle replay farther down the pipeline.”

### 8. How does authentication work?

“Passwords are BCrypt hashes. Login returns a signed, 15-minute access token and a database-backed refresh token. The API is stateless with respect to HTTP sessions. On a protected request, a filter verifies the signature and expiration and loads the current user. The Angular interceptor adds the bearer token only to application API requests.”

### 9. What is refresh-token rotation, and why share refresh requests?

“Each successful refresh invalidates the old refresh token and issues a replacement. The database locks the token record to prevent concurrent reuse. Several UI requests can expire simultaneously, so the Angular auth service shares one in-flight refresh observable and retries the failed requests with the new access token. Without that coordination, concurrent refreshes could race and log out a valid user.”

### 10. Does logout immediately invalidate an access token?

“No. Logout deletes the user's refresh tokens and clears the browser session. Already-issued access tokens remain usable until expiration. Immediate revocation would require a denylist, token version or equivalent stateful check. The current 15-minute lifetime bounds that window.”

### 11. Authentication versus authorization?

“Authentication establishes who the caller is. Authorization decides whether that user owns the requested strategy, run, alert or notification. A valid JWT does not grant access to another user's data. Backtest initiation and result retrieval explicitly enforce ownership; tests attempt execution using another user's strategy ID.”

### 12. Why secure STOMP separately from HTTP?

“The WebSocket upgrade is only the beginning of the session. The STOMP CONNECT frame carries a bearer token, which establishes the principal. Subscription destinations are allowlisted, user queues resolve against that principal, and client SEND frames are rejected because the feed is read-only. Wildcard subscriptions to other users' destinations must not be allowed.”

### 13. Why Redis, and what happens when it fails?

“Redis is an optimization, not the source of truth. Historical responses have a 60-second TTL and latest tick snapshots have a 30-second TTL. If a cache operation fails, historical data is read from PostgreSQL. This trades some temporary database load for availability. TTL also means a bounded freshness window; not every screen is guaranteed to show the newest possible cached bar.”

### 14. Why use Flyway and `ddl-auto=validate`?

“Versioned migrations define the intended schema change history. Hibernate validates the mapping rather than silently altering production tables. The migrations create the base schema, tick uniqueness, outbox and notification deduplication fields, and alert delivery selection. H2-based API tests do not prove every PostgreSQL-specific migration; a real PostgreSQL acceptance run is still necessary.”

### 15. What was the serious RSI bug?

“The original initialization loop compared the index with the current list size while adding an item on each iteration, so the stopping condition never became false. That could allocate until memory exhaustion. The replacement uses a fixed-size array and bounded loops. Tests run all three strategies under a timeout and check flat-price behavior, finite results and parameter rejection.”

### 16. How do you avoid look-ahead bias?

“A strategy's signal at an execution bar uses only previous completed bars. A previous-close signal executes at the next bar's open. I do not use the current bar's close to claim a fill at the same close. A regression test changes future prices and checks that earlier equity points stay unchanged. This addresses information leakage, but not survivorship bias or inaccurate corporate-action data.”

### 17. Explain SMA, RSI and MACD in this implementation.

“SMA compares fast and slow simple averages and trades their crossovers. RSI uses smoothed average gains and losses and threshold-based entry/exit. Flat prices map to RSI 50, and rising prices with no losses map to 100. MACD subtracts slow EMA from fast EMA and compares that line with an EMA signal line. These are long-only simulations.”

### 18. Why are win rate and profit not enough?

“A high win rate can hide infrequent large losses. I also report return and maximum peak-to-trough equity drawdown. The equity curve shows the path of returns, and closed-trade count helps identify tiny samples. A stronger research platform would add out-of-sample evaluation, benchmarks, risk-adjusted metrics, realistic fills, slippage, taxes and corporate actions.”

### 19. Why a separate async worker?

“Spring's async behavior uses a proxy. Calling an annotated method from another method on the same object can bypass that proxy. The controller first calls a transactional service that commits PENDING, then calls a separate worker bean. The worker records RUNNING in a short transaction, computes outside that transaction, and persists its terminal result. A recovery scheduler can redispatch pending and stale-running jobs.”

### 20. How does the Angular UI manage state?

“Standalone components use signals for local view state, such as current symbol, loading status and results. RxJS handles HTTP, shared refresh work, stream cancellation and polling. Guards redirect unauthenticated navigation. Subscriptions for changing symbols and run polling are cleaned up. NgRx would be useful with a much larger shared state model, but here it would add machinery without a clear benefit.”

### 21. How did you test it?

“Engine tests cover strategy termination, flat prices, fees, invalid periods, insufficient history and future-data independence. API tests cover protected routes, refresh rotation/reuse, secret-free responses, invalid thresholds, cross-user access, deduplication and asynchronous run completion. Angular tests cover bearer scoping, shared refresh and SVG chart output. An embedded Kafka test exercises event transport. I distinguish these checks from the still-required real-container and visual-browser acceptance checks.”

Consult `VERIFICATION.md` for the final test count and actual results. Never describe an added test as passing unless its result is recorded there.

### 22. What would you change for scale?

“Partition by the key whose order matters; measure consumer lag and alert-query cost; index active alert lookup; batch where justified; enforce data retention; paginate growing histories; move CPU-heavy backtests onto dedicated workers; and use a shared WebSocket broker or gateway fan-out. A simple broker and a shared Kafka consumer group are not sufficient to broadcast every tick to browsers attached to multiple application instances.”

### 23. What are the security tradeoffs?

“Session storage avoids persisting tokens across browser sessions, but it is still readable by injected JavaScript. A stronger public deployment could use HttpOnly refresh cookies with explicit CSRF protection, CSP, rate limiting, email verification and secret rotation. The development JWT key is never a production secret. A local demo should not be described as a completed security audit.”

### 24. What is the strongest debugging story?

“I compared the execution plan with actual behavior instead of counting class files. I found that the frontend was absent while the backend looked structurally complete. Then I traced a user flow and found the security dependency cycle, missing ownership checks, swallowed Kafka failures, the unbounded RSI loop, fixed-symbol backtesting and ignored date ranges. I repaired the cross-layer flows and added targeted regression tests rather than only adding UI screens.”

## Five-minute demonstration

1. Explain that the demo replays real historical daily candles from March 2020, not current market quotes, and create an account. See [source and replay details](HISTORICAL_DATA.md).
2. Open the dashboard, inspect daily candles and their source, switch to replay prices and add the symbol to the watchlist.
3. Create a price-above alert below the displayed replay price. Explain one-shot triggering and the Kafka/outbox path.
4. Open notifications, refresh if necessary and mark the alert read. Use the in-app channel unless SMTP is configured.
5. Run SMA or RSI over March 2020. Explain the 83 earlier warm-up sessions, next-bar execution, fees, final liquidation and drawdown.
6. Show tests and discuss one limitation honestly: real provider integration, production infrastructure validation, or multi-instance WebSocket distribution.

## Statements to avoid

- “This is a production trading system.” It is a research/demo application with no order execution.
- “Kafka guarantees exactly once everywhere.” Database and SMTP boundaries require separate reasoning.
- “JWT means the application is fully stateless.” Refresh tokens, user records and authorization state live in the database.
- “A profitable backtest proves a strategy works.” Data quality, costs, bias and out-of-sample performance matter.
- “All end-to-end checks passed.” Use the verification record and identify environment-blocked checks.
