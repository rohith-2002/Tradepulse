# TradePulse: detailed interview study material

> Runtime update (19 October 2026): the repeating historical replay described below has been replaced by continuous synthetic ticks calibrated from historical data, with forming 1-minute candles and live strategy evaluation. Use [the current simulation notes](CONTINUOUS_SIMULATION.md) for feed behavior and limitations; the outbox, authentication and fixed historical-backtest concepts still apply.

Prepared 19 October 2026 from the current project implementation. Companion: [short project explanation](PROJECT_EXPLANATION.md).

## How to study this guide

Practise each main answer aloud, then answer its two cross-questions without looking. Explain **what happens, why it was designed that way, what can fail, and how you would improve it**. The answers distinguish existing behavior from proposed improvements. Do not present proposed changes as completed features or claim measurements you have not made.

Prioritize questions 3–12, 16–18, 22–28 and 32–35 for a backend/full-stack interview. Use the source map at the end to find concrete evidence before a technical round.

## A. Architecture and Spring fundamentals

### 1. What problem does TradePulse solve?

**Answer:** TradePulse is a market research and strategy-simulation application. A user can inspect stock candles, follow changing prices, create one-shot price or volume alerts, review notifications, and backtest SMA, RSI or MACD strategies. Angular provides the interface; a Spring Boot backend handles security and business rules; PostgreSQL stores durable state; Kafka transports events; Redis supports caching. The demonstration uses recorded prices, not invented candles or a live exchange feed. It does not execute brokerage orders.

**Cross-question: What is your main engineering contribution?**

The strongest explanation is connecting complete flows rather than listing technologies: authenticated requests, reliable alert-to-notification processing, asynchronous backtests with controlled date boundaries, and a UI that still displays a recorded price when streaming fails. Discuss the parts you personally understand and can demonstrate; do not invent individual contributions or team experience.

**Cross-question: How would you measure whether the product works well?**

Measure quote freshness, alert-to-notification latency, duplicate notification rate, backtest completion time, consumer lag and error rates. No production latency or throughput benchmark is established in this project, so I would describe these as metrics to collect rather than quote imaginary performance numbers.

### 2. Is this a microservices architecture?

**Answer:** It is a modular monolith with a separate reusable Kafka library and an Angular application. The backend has packages for authentication, market data, alerts, backtests, notifications and infrastructure. These packages share one backend deployment and database. The Maven reactor coordinates building the backend and Kafka library. Using Kafka does not, by itself, make the application a collection of microservices.

**Cross-question: Why not split everything into independent services?**

Independent services add network failures, deployment coordination, distributed authorization and consistency problems. For this scope, one deployment keeps operations and transactions manageable. I would extract a module when measured workload or organizational ownership justifies it, not merely to increase the service count.

**Cross-question: Which module would you extract first?**

CPU-heavy backtests are a plausible candidate because their workload differs from interactive APIs. A dedicated worker could consume durable job requests and persist results. That requires explicit job ownership, retry semantics and compatible contracts; moving a class into another repository is not sufficient.

### 3. Walk through a price update from beginning to end.

**Answer:** The demo feed selects a recorded April candle and creates a price event with OHLC, daily volume, original source date and current delivery timestamp. It updates its in-memory current-quote map and publishes through `MarketTickProducer`. The Kafka consumer invokes a handler that persists the tick, evaluates alerts, and broadcasts the event through STOMP. Angular updates the displayed price and revealed candles. A separate REST quote endpoint reads the current replay quote, and the dashboard polls it as a fallback.

**Cross-question: Does a price visible in Angular prove the tick was committed to PostgreSQL?**

No. The REST replay quote can come directly from memory before Kafka consumption and persistence. The displayed quote, durable tick storage and alert processing are related but distinct stages. A production UI could expose processing freshness separately from source freshness.

**Cross-question: Does polling guarantee uninterrupted movement during a Kafka outage?**

No. It can return the last available in-memory quote without waiting for consumer processing. The producer call itself can still block or fail when Kafka is unavailable, slowing scheduled replay. Decoupling replay advancement from publication with a bounded delivery mechanism would be a further improvement.

### 4. Why use dependency injection and constructor injection?

**Answer:** Spring creates services and supplies their collaborators, so business classes do not manually instantiate repositories, producers or mail clients. Most project services use final fields and Lombok-generated constructors. Dependencies are explicit, can be replaced with test doubles, and are available when construction finishes. The Kafka library defines handler interfaces; the application supplies business implementations through configuration beans.

**Cross-question: What is inversion of control here?**

The framework controls object creation and lifecycle. For example, the consumer depends on a handler contract, while application configuration supplies the actual handler. This separates event transport from business processing without requiring reflection or service lookups in every method.

**Cross-question: How did you avoid a circular security dependency?**

The security configuration supplies user lookup and authentication beans. Injecting the JWT filter into that configuration's constructor can create a cycle through dependencies produced by the same configuration. The filter is instead supplied as a parameter to the security-filter-chain bean method, avoiding that constructor dependency cycle.

### 5. What does `@Transactional` guarantee in this application?

**Answer:** It establishes a database transaction around a proxied service invocation. Related SQL changes can commit or roll back together. For example, changing an alert to TRIGGERED and inserting its outbox row are one database transaction. `EventOutbox.enqueue` uses MANDATORY propagation, so it requires a caller transaction. This prevents accidental standalone event-intent writes outside the intended business boundary.

**Cross-question: Does a transaction roll back a Kafka message or an email?**

Not this database transaction. External side effects require their own coordination. The outbox addresses the database-to-Kafka publication gap, while email still has a send-before-commit uncertainty window. It would be incorrect to describe the whole event chain as one atomic transaction.

**Cross-question: Is tick persistence plus alert evaluation one transaction?**

Not in the current handler. It calls separately transactional service methods: `market.ingest`, then `alertService.evaluate`, then WebSocket broadcast. If evaluation fails after ingestion commits, retry can find the existing tick and continue evaluation. This is retry-based recovery across boundaries, not an all-or-nothing transaction covering the complete handler.

### 6. Why use a separate asynchronous backtest worker?

**Answer:** HTTP requests should not remain occupied while a simulation runs. The API persists a PENDING run and dispatches work to a separate `@Async` bean. The worker claims the run and records RUNNING in a short transaction, calculates outside a long database transaction, then persists the final result and outbox event together. The client receives a run identifier and polls for completion.

**Cross-question: Why not call an `@Async` method on `this`?**

Spring's normal proxy-based interception is bypassed by self-invocation. Calling a separate injected worker bean goes through the proxy and allows asynchronous execution. Similar reasoning applies to transaction annotations: the annotation alone is not proof that interception occurred.

**Cross-question: What if the executor rejects work?**

A persisted PENDING run can remain available for recovery. The recovery scheduler attempts redispatch and stops its batch when the executor rejects a task. A larger system would expose queue capacity and saturation metrics, apply admission control, and use a durable worker queue with explicit retry policy.

## B. Kafka, reliability and concurrency

### 7. Why Kafka instead of directly calling notification code?

**Answer:** Kafka separates event production from downstream consumption, allows independent consumers, and buffers work while consumers are temporarily unavailable. It also supports replay within retained history. Direct synchronous calls would be simpler for a small application, but would couple the response path to notification availability and latency. Kafka adds infrastructure and operational responsibility; that tradeoff should be acknowledged.

**Cross-question: Does Kafka replace PostgreSQL?**

No. PostgreSQL is the source of truth for accounts, alert state, runs and inbox records. Kafka carries events and retains a stream according to broker policy. Reconstructing all business state solely from events would require an event-sourcing design, which this project does not implement.

**Cross-question: What is the role of the Kafka library module?**

It contains event contracts, producers, consumers, handler interfaces and transport configuration. Application beans implement domain behavior. This makes transport reusable, although event-schema changes still require compatibility planning between publishers and consumers.

### 8. How do partitions, keys and consumer groups affect processing?

**Answer:** The market producer uses the symbol as the message key. For a stable partitioning setup, a symbol's records go to the same partition and have partition order. The local topics have three partitions and one replica. Consumers in the same group divide partitions; different groups can independently consume the stream. There is no global order across all symbols or topics.

**Cross-question: Will six consumers make a three-partition topic six times faster?**

No. In a conventional consumer group, at most three consumers can actively own those three partitions at once. More consumers may be idle for that topic. Actual throughput also depends on database work, processing cost, key skew and configured listener concurrency; the project has no measured scaling claim.

**Cross-question: Can one very active symbol create a bottleneck?**

Yes. A hot key can concentrate traffic on one partition. Splitting that key can improve parallelism but sacrifices simple per-symbol ordering. I would first measure lag by partition and determine whether processing can be safely divided by an additional key or stage.

### 9. What is the dual-write problem, and how does the outbox solve it?

**Answer:** Updating PostgreSQL and publishing to Kafka are two separate operations. If the database commits but the process crashes before publishing, the business state changes without an event. If publication happens first and the database later rolls back, an event can describe a change that never committed. The outbox puts business state and publication intent in the same PostgreSQL transaction. A scheduled publisher later sends committed intent records to Kafka.

**Cross-question: Describe the implementation precisely.**

Every two seconds, `OutboxPublisher` selects up to 25 unpublished rows in ID order with `FOR UPDATE SKIP LOCKED`. It deserializes each approved event type, waits up to five seconds for the Kafka send result, then marks the row published. The query locks and updates run inside a database transaction.

**Cross-question: What if Kafka accepts the event and the process crashes before marking it published?**

The row remains eligible, so publication can happen again. This is why the outbox provides retryable, at-least-once publication rather than exactly-once end-to-end delivery. Consumers must recognize the same logical event when it is repeated.

### 10. Is the outbox implementation production-ready as written?

**Answer:** It demonstrates the essential transactional boundary and retry behavior, but has operational limitations. It holds database locks while waiting on network sends, and a failure breaks the batch. A permanently invalid early event can repeatedly block progress for a single publisher. There is no complete operational UI for failed events, age-based alerting or cleanup of published records.

**Cross-question: Why `SKIP LOCKED`?**

It lets another publisher skip rows already claimed by a transaction rather than block on them. This can support concurrent workers, but separate workers may publish different batches out of global order. A requirement for strict aggregate ordering needs additional design; ordering by SQL ID alone is not a universal guarantee.

**Cross-question: How would you improve it?**

Add attempt counts, last-error metadata, bounded retry/backoff and quarantine for poison records. Consider a short claim transaction with leases or a change-data-capture publisher to reduce lock duration. Add pending-age and publication-failure metrics, and ensure all approaches still tolerate duplicate publication.

### 11. How do you prevent duplicate notifications?

**Answer:** Alert-derived notification events use a stable ID such as `alert-<alertId>`. Notification dispatch locks the user row, checks whether that event ID already exists, and persists a notification with a unique event-ID constraint. The lock serializes same-user dispatches, while the unique constraint is the final database safeguard. Repeated notification events should therefore not create repeated inbox rows for that stable ID.

**Cross-question: Why not just check `existsByEventId` without a lock or unique constraint?**

Two transactions can both observe that the row is absent and then both insert. A pre-check is not concurrency protection by itself. The current lock and database uniqueness address that race. An alternative is atomic insert-on-conflict handling using a properly designed idempotency key.

**Cross-question: Does this mean every event is idempotent?**

No. The guarantee depends on a stable, non-null event ID and the specific business boundary. An event without that identifier does not receive the same deduplication guarantee. Intermediate outbox rows may also repeat even when the eventual inbox insertion is deduplicated.

### 12. Can email be delivered exactly once?

**Answer:** The current implementation cannot promise that. It sends email and then saves the notification within the database transaction. If the mail server accepts the email and the database commit fails, a retry may send the email again. Conversely, marking success before sending would risk losing delivery after a crash. The database cannot undo an accepted SMTP side effect.

**Cross-question: Would Kafka transactions fix this?**

No. Kafka transactions can coordinate Kafka operations, but do not automatically enlist an external mail server or PostgreSQL transaction. I would describe guarantees separately for Kafka publication, durable inbox insertion and external email delivery.

**Cross-question: What would improve email reliability?**

Use a durable delivery job, an explicit attempt state machine, provider message IDs, and a provider-supported idempotency key if available. Record ambiguous outcomes rather than pretending a timeout proves nothing was sent. The delivery policy must decide how to balance missing messages against possible duplicates.

### 13. What happens when a Kafka consumer fails?

**Answer:** Exceptions are allowed to reach the error handler rather than being swallowed. `KafkaReliabilityConfig` uses a fixed backoff of one second with up to three retries for retryable failures, then a dead-letter publishing recoverer. Dead-letter topics use the `.DLT` suffix, and publication failure is configured to surface as an error. Some errors can be classified as non-retryable by the framework; I would not claim that every possible failure receives exactly the same retries.

**Cross-question: Is adding a DLT enough?**

No. Someone must detect the backlog, inspect the failure, correct its cause and replay safely. Replaying must preserve the logical event ID so downstream deduplication still works. Retention and access control also matter because failed events can contain business data.

**Cross-question: What is the difference between transient and poison failures?**

A temporary database outage might succeed on retry. A malformed event or unsupported schema may fail every time without a code or data correction. Retrying poison events indefinitely wastes resources and can delay healthy work.

### 14. What does `acks=all` guarantee?

**Answer:** It requests acknowledgment according to Kafka's in-sync replica rules. It is not a claim of unlimited durability. The local project topics use replication factor one, so there is no second replica protecting against that broker's loss. Durability depends on replication, in-sync replica policy, storage and failure configuration together.

**Cross-question: Is producer idempotence the same as business idempotency?**

No. Producer idempotence addresses certain producer retry duplicates. It does not deduplicate a new logical send caused by an outbox retry after a crash, or undo repeated email delivery. The application's stable event ID addresses a different layer of duplication.

**Cross-question: How would you prepare Kafka for production?**

Use an appropriately replicated cluster, explicit durability settings, authenticated/encrypted connections, ACLs, monitoring, retention policies and a capacity plan. None of those should be claimed solely because the single-broker development Compose stack starts successfully.

### 15. How do concurrent ticks avoid triggering the same alert twice?

**Answer:** Alert evaluation retrieves active alerts using a pessimistic write lock inside a transaction. A matching condition changes the row to TRIGGERED and writes the outbox intent before commit. Later processing should no longer find it as active. The implementation protects the state transition in the database rather than relying only on normal Kafka symbol ordering.

**Cross-question: Pessimistic or optimistic locking—which is better?**

Pessimistic locking acquires a lock before the change; optimistic locking detects conflicting versions at update time and requires retry logic. The current design chooses pessimistic locking for the one-shot transition. The best choice depends on contention, transaction duration and acceptable retries, not a universal rule.

**Cross-question: What limitation would you examine first at scale?**

Fetching and locking every active alert for a symbol can be expensive. The current schema also needs an alert-lookup indexing review. I would measure query plans and contention, then consider a composite index, threshold-based candidate filtering or conditional atomic updates without weakening one-shot semantics.

## C. Authentication, authorization and secrets

### 16. Explain login, access tokens and refresh tokens.

**Answer:** Registration normalizes email and hashes the password with BCrypt. Login verifies the supplied password against that hash. A signed access token identifies the user and expires after 15 minutes. A UUID refresh token is stored in PostgreSQL with a configured seven-day lifetime. Refresh locks the token record, rejects invalid or expired tokens, removes the old valid token and issues a replacement along with a new access token.

**Cross-question: Is a JWT encrypted?**

The access tokens here are signed, not encrypted. A signature detects tampering; it does not hide the claims from someone holding the token. Passwords and secrets must not be put inside claims merely because the token looks encoded.

**Cross-question: Why hash passwords rather than encrypt them?**

Authentication needs to verify a candidate password, not recover the original. BCrypt performs a deliberately costly salted password hash. Reversible encryption would introduce a decryption key capable of exposing all passwords. The project uses the password encoder rather than implementing its own hashing algorithm.

### 17. What happens during simultaneous refresh requests and logout?

**Answer:** On the server, a pessimistic lock serializes use of a refresh-token record. In Angular, `Auth.refresh` shares one in-flight HTTP observable using `shareReplay`; several failed requests wait for that single refresh and retry with the resulting access token. Logout deletes the user's refresh tokens and clears the browser session. It does not maintain an access-token denylist.

**Cross-question: Can an already issued access token still work after logout?**

Yes, until it expires, unless another server-side condition rejects it. Immediate revocation would need an additional mechanism such as token-version checks or a denylist. Describing logout as instantly invalidating all access tokens would overstate the implementation.

**Cross-question: Are refresh tokens stored securely enough for a public deployment?**

They are currently stored as raw token values, which is a limitation if the database is compromised. Hashing stored refresh tokens, token-family reuse detection and stronger session controls would improve this. Rotation is implemented, but it is not a complete token-theft response system.

### 18. How do you prevent one user from accessing another user's data?

**Answer:** JWT authentication establishes identity, but ownership checks enforce authorization. Strategy execution and backtest result access check the strategy owner; notification read operations check the notification owner. Alert listing is scoped to the current user. A valid token alone does not authorize arbitrary object IDs supplied by the client.

**Cross-question: Does an Angular route guard provide security?**

It improves navigation behavior, but a caller can bypass Angular and invoke HTTP endpoints directly. The backend must enforce identity, ownership and input rules independently. The API tests include cross-user strategy access to exercise this boundary.

**Cross-question: Why sometimes return 404 rather than 403?**

A not-found response can avoid confirming the existence of another user's resource. The project uses not-found behavior in several owner checks, but responses are not a uniform authorization policy everywhere. Alert deletion, for example, silently does nothing for an unauthorized owner rather than exposing the record.

### 19. How is WebSocket access protected?

**Answer:** The WebSocket handshake endpoint is permitted, but the STOMP CONNECT frame must carry a valid bearer token. The server validates it and establishes a principal. SUBSCRIBE permits only a concrete market-symbol topic or specific private user queues. Client SEND frames are rejected because clients should not publish market events through this connection. Allowed browser origins are configured separately.

**Cross-question: Does an allowed origin prove who the user is?**

No. Origin restrictions and authentication solve different problems. A permitted origin does not establish account identity, and a token does not justify allowing arbitrary subscription destinations. Both checks belong in the connection policy.

**Cross-question: Is token expiration continuously enforced on an established socket?**

The implemented check is at CONNECT; subscription checks use the established principal. The code does not implement a timer that disconnects a session exactly when its JWT expires. Reconnect also needs a valid current token; coordinated refresh/reconnect handling is a future improvement.

### 20. How are secrets and browser tokens handled?

**Answer:** Runtime secrets are provided through environment configuration. Non-demo mode requires a JWT secret. Demo mode generates an ephemeral signing key if none is supplied, so old access tokens cannot be validated after a restart with a different key. Local environment files, keys, IDE files, logs and caches are excluded from Git. The test signing key is a public fixture, not a runtime credential. Angular keeps its session in session storage.

**Cross-question: Is session storage safe against XSS?**

No. JavaScript running in the origin can read it. Avoiding persistent local storage does not eliminate token theft through XSS. A stronger public deployment could use HttpOnly refresh cookies, CSP and carefully reviewed rendering, while explicitly addressing CSRF and cookie configuration.

**Cross-question: Why is CSRF disabled, and what would change with cookies?**

The current HTTP API uses bearer headers rather than automatically attached session cookies. That changes the usual CSRF threat path, but does not remove XSS or authorization risks. Introducing cookie-based credentials requires revisiting CSRF protection rather than retaining the current setting without analysis.

## D. Historical data and backtesting

### 21. Where does the market data come from, and what does “future data” mean?

**Answer:** The bundled snapshot comes from a public GitHub archive of NSE historical data, pinned to a revision with individual CSV hashes. It contains six stocks: RELIANCE, ICICIBANK, ITC, HDFCBANK, INFY and TCS. Each has 83 warm-up sessions from November 2019 through February 2020, 21 March sessions for the starting history, and 18 April sessions for continuation. The April candles are future relative to the simulation's March starting point, not predictions of actual future prices.

**Cross-question: Why call it a simulation when the prices are real historical observations?**

The observations are historical, but their delivery speed and timing are simulated. One completed daily candle is revealed every three seconds, and April repeats. An actual market does not trade an entire day's volume every three seconds. This is accelerated daily replay, not an intraday exchange feed.

**Cross-question: Does a public download mean the data is verified and openly licensed?**

No. The archive's redistribution license and adjustment methodology are unspecified. Structural validation and pinned provenance support reproducibility, but do not prove independent exchange reconciliation or licensing rights. I would verify source terms and corporate-action handling before any public commercial use.

### 22. How do you validate historical candles and measure volatility?

**Answer:** The import script checks positive prices, high/low consistency, nonnegative volume, unique chronological dates and sufficient history. Tests check that the six instruments share the expected calendars and reproduce the volatility calculation. Daily volatility is the sample standard deviation of within-March log close returns, multiplied by 100. For these six stocks, it ranges from approximately 4.49% to 7.26%; it is not annualized.

**Cross-question: Give the formula and interpretation.**

For consecutive closes, `r[t] = ln(close[t] / close[t-1])`. Calculate the mean of those returns, then `sqrt(sum((r - mean)^2)/(N - 1)) * 100`. Twenty within-month returns come from 21 March closes. The result summarizes observed variation in that sample, not a guaranteed daily movement or forecast.

**Cross-question: Why not select a highly volatile month and declare the strategy robust?**

Choosing a stress month is useful for exercising sharp moves, but creates selection bias. Robustness needs additional periods, different market conditions and a predefined evaluation procedure. High volatility is not the same as realistic execution, good liquidity or profitable strategy behavior.

### 23. What is indicator warm-up, and why is it separate from the trading period?

**Answer:** Indicators require earlier observations to produce useful values at the first trading date. March has only 21 sessions, while the default slow period is 26. The worker fetches earlier bars, but passes the requested trading start separately. The engine initializes indicators using history and only begins trades and reported equity inside the requested window. The bundled 83 earlier sessions support the default strategies.

**Cross-question: What happens if a user requests a very large period?**

The engine validates period bounds and requires enough pre-window observations. If the available warm-up is insufficient, it fails explicitly instead of inventing observations or silently trading with incomplete inputs. A valid numerical parameter is not necessarily supported by a particular dataset window.

**Cross-question: Does the warm-up length prove EMA initialization error is zero?**

No. EMA is seeded from the first available value, and earlier history reduces its initialization influence rather than proving exact equivalence to an infinitely long series. The current engine also uses `slow + signal` for both SMA and MACD warm-up; this is conservative for SMA rather than its mathematical minimum.

### 24. How is look-ahead bias prevented?

**Answer:** At execution index `i`, the engine reads the signal from the previous completed bar, `i - 1`, and executes at bar `i`'s open. Crossover detection also uses the preceding signal state. The worker limits its query to the requested end date, so April bars are excluded from a March backtest. A regression test changes future prices and checks that earlier equity points do not change.

**Cross-question: Give a simple example.**

If a crossover becomes known from Monday's closing price, a next-bar model can execute at Tuesday's opening price. Buying at Monday's opening price using Monday's eventual close would use information unavailable at the time. A daily candle alone does not establish a tradable fill at the same closing price used to calculate the signal.

**Cross-question: Is preventing look-ahead enough to make results realistic?**

No. The model still omits slippage, taxes, liquidity constraints, detailed corporate actions and realistic order execution. Survivorship bias, parameter overfitting and selecting a favorable month can remain even when each signal uses only past bars.

### 25. Explain SMA and EMA, including computational complexity.

**Answer:** An N-period SMA is the arithmetic mean of the last N closes. The implementation maintains a rolling sum: add the current close and subtract the close leaving the window. That computes the full series in O(number of bars) time instead of resumming each window. EMA uses `EMA[t] = EMA[t-1] + alpha * (price[t] - EMA[t-1])`, with `alpha = 2/(N+1)`, also taking linear time. Indicator arrays and result lists use linear memory.

**Cross-question: Why use crossovers rather than simply buying whenever fast SMA exceeds slow SMA?**

The implemented signal detects a change from fast-at-or-below-slow to fast-above-slow. It avoids repeatedly treating the same ongoing relationship as a new crossover. A strategy based on persistent state is possible, but would have different entry behavior at the start of the evaluation window.

**Cross-question: What if the fast average is already above the slow average on March's first day?**

The engine does not automatically enter merely because of that state; it requires the defined crossover condition. It also begins with no position from warm-up. This is an important modeling choice to explain rather than assume a position was opened before the requested period.

### 26. Explain RSI and its edge cases.

**Answer:** RSI compares smoothed average positive and negative close-to-close changes. With `RS = averageGain / averageLoss`, `RSI = 100 - 100/(1 + RS)`. The engine initializes averages over the period and then applies Wilder-style smoothing. It buys below the oversold threshold and sells above the overbought threshold. The UI defaults to period 14 and thresholds 30/70.

**Cross-question: What if all prices are flat or there are no losses?**

Flat prices produce RSI 50 in this implementation. Positive gains with zero average loss produce RSI 100. Handling these cases explicitly avoids division-by-zero and non-finite results, and the flat-market regression checks that no artificial profit is generated.

**Cross-question: What was the earlier RSI implementation defect?**

The project review records an initialization loop whose stopping bound grew as the list grew, preventing termination. The replacement uses bounded loops and fixed arrays. The lesson is to reason about loop invariants and allocation behavior, not only check the finance formula. Only tell this as a personal debugging story if you can explain the actual change confidently.

### 27. Explain MACD and the strategy parameters actually used here.

**Answer:** MACD is fast EMA minus slow EMA. A signal line is an EMA of the MACD line. The strategy buys on an upward MACD/signal crossover and exits on a downward crossover. The current execution defaults are fast 9, slow 26 and signal 9. I would state those actual defaults rather than automatically recite a different conventional parameter set.

**Cross-question: Is MACD an independent input from price?**

No. It is a transformation of historical closes and necessarily lags them. Combining several price-derived indicators does not automatically create independent evidence or remove model risk. Their behavior depends on periods and the price path.

**Cross-question: Can you optimize the periods to maximize March profit?**

Technically yes, but reporting the best result on the same month used to choose parameters would overfit the sample. I would freeze parameters using one period and evaluate on another, with a documented search procedure and realistic costs. April is a continuation dataset, not proof of an already implemented optimization pipeline.

### 28. How are fees, equity and drawdown calculated?

**Answer:** The engine uses long-only fractional positions. For cash C, opening price P and per-side fee fraction f, entry quantity is `C / (P * (1 + f))`. Exit cash is `quantity * exitPrice * (1 - f)`. Equity is cash plus the position valued at the current close. The engine tracks the highest equity so far and computes drawdown as `(peak - equity)/peak`, retaining the largest value. Any remaining position is liquidated at the final close.

**Cross-question: What does 10 basis points mean?**

Ten basis points is 0.10%, so f is 0.001. With cash 100,000 and price 1,000, quantity is about 99.9001 after allowing for entry fees. If sold at the same price, exit fees reduce cash to about 99,800.20. Flat execution prices do not imply zero loss once costs are included.

**Cross-question: Why is win rate insufficient?**

A strategy can win frequently but suffer a few very large losses. Return, drawdown, trade count and the equity path provide more context. This project reports those measures, but does not establish statistical significance or automatically calculate every risk metric an interviewer might name.

### 29. Why use `BigDecimal` in storage but `double` in the backtest engine?

**Answer:** Database prices use fixed-precision decimal columns and Java `BigDecimal`, preserving explicit decimal representation and scale for persisted OHLCV and threshold comparisons. The numerical indicator engine converts to doubles for array-based calculations. That is practical for this research simulation, but binary floating-point arithmetic and rounding mean it should not be presented as an exact accounting ledger.

**Cross-question: Should all financial calculations use the same numeric type?**

Different requirements justify different representations. Indicators often use floating point; cash accounting and order settlement need explicit precision and rounding rules. A production trading system should specify lot sizes, tick sizes, currency scales and rounding policy rather than assume four-decimal inputs make every output exact.

**Cross-question: Can daily OHLC tell whether a stop loss or target happened first?**

No. The high and low reveal extremes, not the order of intraday events. Inferring a favorable execution sequence from them would create unsupported fills. This engine uses next-open entries/exits and final-close liquidation rather than claiming intraday stop/target reconstruction.

### 30. How does backtest crash recovery work, and what is its limitation?

**Answer:** A scheduler runs every 30 seconds and redispatches batches of PENDING jobs and RUNNING jobs whose start time is more than 15 minutes old. The worker locks the run while checking and changing its status, so ordinary concurrent claims are serialized. Results and their publication intent are committed together after computation.

**Cross-question: What if an original worker is still computing after 15 minutes?**

It can overlap a replacement worker. The current implementation lacks an execution-generation token or lease heartbeat, and the final save does not fence out the older worker. Row locking during claim is therefore not a complete exactly-once execution guarantee. A stale worker could overwrite a newer result or produce an additional result event.

**Cross-question: How would you fix that?**

Give each claim an attempt ID or monotonically increasing generation, renew a lease while working, and make terminal writes conditional on the active generation. Add optimistic version checks or equivalent conditional SQL and idempotent result publication. Cancellation also needs a cooperative policy; it is not implemented simply by changing a status value.

## E. Database, caching and API design

### 31. Why PostgreSQL, relational tables and JSONB?

**Answer:** Users, symbols, alerts and notifications have relationships and constraints that fit relational tables. PostgreSQL transactions protect related state changes, while foreign keys and unique constraints enforce integrity. Strategy parameters and backtest results use JSONB because the fields differ by strategy and result shape. Application validation still determines what those JSON structures mean.

**Cross-question: Why not put everything in JSONB?**

Frequently queried identities, ownership and status benefit from explicit columns, constraints and indexes. Storing every relation inside JSON would complicate integrity checks and common queries. Flexible parameter payloads are a different use case from user IDs and alert status.

**Cross-question: Which indexes matter in the current workload?**

Ticks are indexed by symbol and descending timestamp; candles have symbol/interval/time indexing and a uniqueness constraint; the outbox has a partial index for unpublished rows. Active-alert lookup and growing run/inbox lists deserve additional query-plan and pagination work. An index should be justified by actual predicates and data volume.

### 32. Explain the Redis cache and fallback behavior precisely.

**Answer:** For non-replay history, the service attempts a Redis read, queries PostgreSQL on a miss or cache exception, and stores the result for 60 seconds. Tick ingestion also writes a latest-price snapshot with a 30-second TTL. Replay history bypasses the history cache because the visible date range changes as days are revealed. The current quote endpoint reads replay memory, stored recent ticks or the latest historical close; it does not read that latest-price Redis key.

**Cross-question: Does Redis being down stop the application?**

History cache exceptions are caught and the database path remains available. However, fallback increases database traffic and cache timeouts can still add latency. Availability fallback should be accompanied by metrics, sensible timeouts and capacity planning rather than silent assumptions that failure has no cost.

**Cross-question: What is a cache stampede?**

Many requests can miss the same expired key and query the database together. Request coalescing, bounded locking, TTL jitter or prewarming can mitigate this. Those protections are proposals here; the current cache primarily provides TTL-based reuse and failure fallback.

### 33. How do migrations and database constraints protect correctness?

**Answer:** Flyway records ordered schema migrations, while Hibernate uses validation rather than silently evolving tables. The migrations cover the base schema, tick uniqueness, outbox and notification event IDs, alert delivery channel and replay provenance. Candle uniqueness prevents two rows for the same symbol, interval and bar time. Tick uniqueness protects symbol/timestamp identity, and notification event IDs protect logical notification identity.

**Cross-question: Does an `exists` check prevent a concurrent duplicate tick insert?**

Not alone. Two transactions may pass the check before either inserts. The unique database constraint provides the final safeguard, though a losing insert may still throw and require appropriate retry/error handling. The existence check is an optimization or common-path guard, not a substitute for the constraint.

**Cross-question: Are H2 tests enough to validate Flyway on PostgreSQL?**

No. The test profile disables Flyway and uses H2 schema creation, so it does not prove the PostgreSQL migration scripts work on a real deployment. PostgreSQL-backed integration tests, such as container-based tests with Flyway enabled, would close that gap. Do not claim those have already been run.

### 34. What API and persistence improvements would you make?

**Answer:** The APIs already validate ownership and common input bounds, but some responses expose domain entities with selected relationships hidden. Dedicated DTOs would make contracts more explicit and reduce accidental coupling to persistence mappings. Large notification/run lists should be paginated, query counts measured, and error responses standardized further. JSON type declarations in Angular do not perform runtime payload validation.

**Cross-question: What are lazy loading and the N+1 problem?**

Lazy loading defers related data until it is accessed. Iterating over many results and triggering one related query per item can create N+1 queries. The project disables open-in-view and uses eager mappings for some returned relations, but eager loading does not automatically guarantee efficient SQL. Fetch plans and query-count tests are better evidence.

**Cross-question: Does parameter validation happen only in Angular?**

No. The backend validates supported strategy types, symbol existence, periods, date ranges, capital and fee bounds, as well as alert thresholds and channels. Browser validation improves feedback; server validation is the trust boundary. Additional business-specific constraints can still be needed.

## F. Angular, streaming and debugging

### 35. Why did the price display become blank, and how was it fixed?

**Answer:** The previous loading flow coupled history and tick requests, so a failure in one could prevent successful history from producing a displayed price. The revised dashboard loads metadata and candles independently, immediately uses the latest loaded close if necessary, accepts WebSocket prices, and polls a current-quote endpoint. If no valid price exists, it displays a dash and an explanation rather than an empty number. This is graceful degradation of a user-visible feature.

**Cross-question: Does fallback mean inventing a current price?**

No. It means showing a known recorded close with its source date. The application must distinguish stale historical data from current observations. A green connection indicator alone would not prove freshness or that Kafka consumers are keeping up.

**Cross-question: How would you debug the same symptom in a deployed app?**

Check the selected symbol, metadata/history/quote HTTP responses, authentication errors, the numeric price field, STOMP connection/subscription state, source timestamps and the running backend version. Then inspect producer/consumer and database status. A successful Angular build cannot compensate for an older server lacking the quote endpoint.

### 36. Why both Angular signals and RxJS?

**Answer:** Signals hold synchronous view state such as selected symbol, current quote, candles, loading and errors. Computed state helps derive values such as chart coordinates. RxJS handles asynchronous HTTP work, polling, refresh sharing, cancellation and stream composition. They have complementary roles: a current state container is not the same thing as an asynchronous event-processing pipeline.

**Cross-question: What does `switchMap` do in the polling flow?**

When a new source emission arrives, it switches to the new inner observable and unsubscribes from the previous one. For quote polling this helps avoid accumulating overlapping requests. If every request takes longer than the polling interval, repeated cancellation can prevent completion; adapting the interval or using a different concurrency policy may be preferable.

**Cross-question: Why put `catchError` inside the polled request?**

It can convert an individual request failure into a recoverable value while keeping the timer subscription alive. If the error terminates the outer polling stream, later successful requests will never occur without explicit resubscription. Cleanup on symbol changes and component destruction is also necessary to prevent old streams updating the new view.

### 37. How do candles move forward and reset during replay?

**Answer:** Price events carry the original source date and recorded OHLC. The UI inserts or replaces a candle by date, sorts the series and updates closing-price values. It avoids adding identical prices repeatedly to the live line for unchanged observations. On a new replay cycle, a later delivery timestamp can legitimately contain an earlier April source date, so the UI removes the previous cycle's April candles while retaining March.

**Cross-question: Why have both source date and delivery timestamp?**

Source date identifies the historical session being represented; delivery timestamp identifies when the replay emitted it. Comparing only source dates would mistake an intentional replay reset for stale network delivery. These are two different time dimensions and should remain separate in storage and display.

**Cross-question: Is replay independently controlled for every user?**

No. It is a shared in-memory progression in the backend instance. Backend restart resets it, and multiple instances could have different cursors. Per-user pause, seek and speed controls would need explicit simulation sessions and state management; those features are not currently implemented.

### 38. Why use both WebSocket updates and HTTP polling?

**Answer:** WebSocket delivery gives immediate updates without waiting for each polling interval. HTTP offers a simple recovery path for the latest quote and is easy to inspect when streaming is unavailable. The backend remains authoritative for the quote; the UI checks ordering and updates the same displayed state from both mechanisms. This is resilience through two delivery paths, not a claim that their consistency is automatic.

**Cross-question: Will this scale without changes?**

Polling every three seconds for every connected dashboard adds predictable request load alongside WebSockets. With many users I would measure that load and poll conditionally when streaming is disconnected or stale, or use a shared subscription/cache layer. Avoid guessing a supported user count without load tests.

**Cross-question: Why not use Server-Sent Events instead?**

For one-way updates, SSE is a reasonable alternative and may be simpler. This project uses STOMP destinations and private user queues over WebSockets. Choosing SSE would require reviewing authentication, reconnection and deployment behavior, but it would not change the need for server authorization and durable state.

## G. Testing, deployment and scaling

### 39. What did you test, and what is still unverified?

**Answer:** The recorded full backend run passed 17 tests, including engine behavior, ownership/security APIs, real-data invariants, replay import and an embedded Kafka pipeline. The Angular retry passed nine tests, including the blank-price fallback and replay rollover. The production Angular build passed. Publishing introduced two signing-key tests; a targeted run of those plus the application-context test passed three tests. That targeted run is not the same as claiming a fresh full 19-test run.

**Cross-question: Which tests provide especially useful evidence?**

Changing future prices without changing earlier equity tests a genuine no-look-ahead property. Importing twice without duplicate candles tests restart behavior. Supplying failed metadata/replay requests while retaining a historical price tests user-visible resilience. Cross-user API requests test authorization rather than only happy-path login.

**Cross-question: What would you test next?**

Real PostgreSQL migrations and concurrent locking, real Redis failure/TTL behavior, broker outages and outbox replay, stale-worker fencing, SMTP ambiguity, and a full browser journey against the deployed containers. The prior verification did not establish a complete visual-browser walkthrough or full PostgreSQL deployment acceptance.

### 40. How would you deploy and scale TradePulse safely?

**Answer:** The development Compose file runs PostgreSQL, Redis, ZooKeeper, Kafka, Kafka UI, backend and frontend. Nginx serves Angular and proxies API/WebSocket paths. Compose health dependencies help initial startup, while named database storage persists across ordinary container recreation. This is a local demonstration topology with loopback-bound ports, public demo defaults and a single Kafka replica—not a complete production platform.

**Cross-question: Why can adding backend replicas break live updates?**

The current STOMP broker is in memory per backend. A Kafka consumer group distributes records among instances rather than broadcasting each record to every instance. A browser connected to instance A might miss an event consumed by B. A shared broker relay or deliberate gateway fan-out is needed; a load balancer alone does not solve delivery.

**Cross-question: What are your first production improvements?**

Secure secrets and transport; use an appropriate database backup/recovery plan and Kafka replication; centralize replay/job ownership; add fencing for long-running jobs; establish metrics and alerts for consumer lag, outbox age and failures; enforce rate limits and retention; and load-test critical paths. Prioritize based on observed risks and workload rather than replacing every component at once.

## H. Tough follow-up scenarios to practise

These questions test whether you can reason beyond a memorized happy-path answer.

| Interviewer challenge | Defensible answer |
|---|---|
| “Your alert is TRIGGERED but Kafka is unavailable. Is the notification lost?” | The committed outbox intent remains for retry. Delivery is delayed. Monitor its age; the presence of an outbox alone does not prove timely delivery. |
| “The same Kafka event arrives twice. Are all side effects deduplicated?” | No blanket guarantee. Tick storage uses symbol/timestamp uniqueness; inbox uses a stable event ID. WebSocket delivery and SMTP have different duplicate behavior. |
| “The user got an email, then your service crashed. Will you send it again?” | Possibly, if the database never recorded successful dispatch. SMTP lacks an atomic commit with this database. |
| “Redis is unavailable. Is every displayed price necessarily fresh?” | No. A known close or previous replay quote can remain available. Availability and freshness must be described separately. |
| “You selected March because it was volatile. Does that make your result unbiased?” | No. It is an intentionally selected stress scenario. Profitability claims require a separate evaluation methodology. |
| “You have April data in the database. How do you stop March from using it?” | The worker's query ends at the requested date, and the engine uses previous-bar signals. Display visibility alone is not the backtest's leakage control. |
| “The user logs out. Why does an old token still validate?” | Access tokens remain valid until expiration without explicit revocation state. Logout removes refresh-token renewal capability. |
| “Why did production fail when H2 passed?” | H2 tests do not prove PostgreSQL migration syntax, dialect semantics, indexes or concurrency behavior. Run database-specific acceptance tests. |
| “A backtest takes 20 minutes. Can two workers finish it?” | Yes, the 15-minute recovery rule can overlap a slow worker. Add leases and fencing rather than claim row-locking at start solves the entire lifecycle. |
| “Does a checksum prove prices are correct?” | It proves byte identity against the pinned file, not the truth of its prices, its adjustment policy or its license. |
| “Is the application highly available because it has Kafka?” | No. The local cluster has one replica, and the backend broker/replay state is local. Availability is an end-to-end deployment property. |
| “Can you guarantee a stop loss was filled before the day's low?” | No. Daily OHLC does not provide the intraday event path or executable liquidity. |

## I. Code-reading map

Paths below are relative to the repository root. Read the implementation beside the related questions; names are evidence locations, not claims that every future improvement is implemented.

| Topic | Source to inspect |
|---|---|
| Security filter chain and password encoder | `tradepulse/src/main/java/com/tradepulse/tradepulse/config/SecurityConfig.java` |
| Access tokens and ephemeral demo key | `tradepulse/src/main/java/com/tradepulse/tradepulse/auth/service/JwtService.java` |
| Login, rotation and logout | `tradepulse/src/main/java/com/tradepulse/tradepulse/auth/service/AuthService.java` |
| Refresh locking | `tradepulse/src/main/java/com/tradepulse/tradepulse/auth/repository/RefreshTokenRepository.java` |
| Historical source, hashes and validation | `scripts/import-market-history.ps1`, `tradepulse/src/main/resources/market/history.json` |
| Replay lifecycle | `tradepulse/src/main/java/com/tradepulse/tradepulse/market/service/DemoMarketFeed.java` |
| Quote fallback and cache policy | `tradepulse/src/main/java/com/tradepulse/tradepulse/market/service/MarketDataService.java` |
| Kafka topic/retry configuration | `tradepulse-kafka/src/main/java/com/tradepulse/kafka/config/` |
| Consumer-to-domain wiring | `tradepulse/src/main/java/com/tradepulse/tradepulse/config/KafkaHandlerConfig.java` |
| Alert conditions and concurrency | `tradepulse/src/main/java/com/tradepulse/tradepulse/alert/service/AlertService.java`, `alert/repository/AlertRepository.java` under the same base package |
| Atomic publication intent and publisher | `tradepulse/src/main/java/com/tradepulse/tradepulse/events/` |
| Inbox deduplication and SMTP boundary | `tradepulse/src/main/java/com/tradepulse/tradepulse/notification/service/NotificationService.java` |
| Indicator formulas and fill assumptions | `tradepulse/src/main/java/com/tradepulse/tradepulse/backtest/engine/BacktestEngine.java` |
| Job claim, persistence and recovery | `tradepulse/src/main/java/com/tradepulse/tradepulse/backtest/service/BacktestWorker.java`, `BacktestRecovery.java` in that directory |
| STOMP authorization | `tradepulse/src/main/java/com/tradepulse/tradepulse/config/WebSocketConfig.java` |
| Angular auth, interceptors and shared refresh | `tradepulse-angular/src/app/core.ts` |
| Dashboard prices and backtest polling | `tradepulse-angular/src/app/pages.ts` |
| Browser STOMP client | `tradepulse-angular/src/app/feed.ts` |
| Regression evidence | Backend `src/test/java`, Angular `src/app/*.spec.ts`, and `docs/VERIFICATION.md` |

## J. Revision plan and interview habits

**First session — explain the system:** Practise the short introduction, draw the event flow, and answer questions 1–6 without naming technologies before explaining their purpose.

**Second session — defend reliability:** Work through questions 7–15. For each failure, identify what has already committed, what can be retried, and which identifier makes a retry safe.

**Third session — defend security:** Work through questions 16–20. Be able to distinguish authentication from ownership, signatures from encryption, and refresh revocation from access-token revocation.

**Fourth session — defend the model:** Work through questions 21–30. Calculate one fee example and one drawdown example, explain warm-up, and identify at least three remaining sources of simulation error.

**Fifth session — run a mock interview:** Answer questions 31–40 and the scenario table. Keep the first response under 90 seconds, then expand when the interviewer probes. Open the relevant source file if asked for evidence.

Avoid absolute claims such as “exactly once everywhere,” “production-ready,” “future price prediction,” “fully stateless,” “Redis guarantees freshness,” or “all deployment tests passed.” A precise explanation of boundaries and tradeoffs is stronger than an exaggerated feature claim.
