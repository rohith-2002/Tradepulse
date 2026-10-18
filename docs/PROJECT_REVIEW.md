# Project review and completion record

Review date: 17 October 2026.

Update, 18 October 2026: the original synthetic demo described below has been replaced by a public historical snapshot for March 2020. All six instruments have 21 visible daily candles and 83 earlier warm-up sessions. The Kafka feed replays recorded closes and daily volumes with original source dates. See [historical data](HISTORICAL_DATA.md) for provenance, migration behavior and limitations. The updated backend clean build passes 17 tests.

## What existed at the start

The original `implementation_plan.md` described three repositories, but only the Java application and Kafka library were present. The backend had entity/repository/controller scaffolding, a first Flyway migration, JWT services, partial indicator algorithms, event schemas and basic producers/consumers. The infrastructure Compose file existed. There was no Angular application, and the only test was an infrastructure-dependent context-load test.

## Findings and implemented changes

| Area | Original gap or defect | Implemented outcome |
|---|---|---|
| Build | Snapshot parent and separately installed local library | Stable Boot 3.5.12 and root multi-module Maven reactor |
| Startup | Security configuration/filter/user-service dependency cycle | Filter injection moved to the filter-chain factory method |
| Configuration | Properties duplicated YAML and overrode environment-based credentials | Single configuration source, environment overrides, separate demo profile |
| Authentication | Logout publicly permitted; unbounded access-token lifetime of 24h | Authenticated logout, 15-minute access tokens, normalized email, locked refresh rotation |
| API serialization | User relationships could expose password hashes and lazy proxies | User relationships ignored in JSON; password hash explicitly ignored; returned symbol/strategy relations loaded |
| Errors | Generic exceptions became opaque server errors | Consistent 400/401/403/404 responses and UI error states |
| Market | No source/seed/persistence pipeline | Explicit synthetic seed/feed, tick consumption/persistence, timestamp deduplication, Redis cache with TTL and fallback |
| Alerts | Percentage condition ignored; validation missing | Four conditions, request validation, row locking, one-shot triggering |
| Event reliability | Consumers swallowed exceptions; database/Kafka dual-write gap | Exceptions reach retries/DLT handling; transactional outbox and confirmed publish before marking sent |
| Notifications | No actual email send or deduplication | Persistent inbox, owner-scoped read, event-ID deduplication, SMTP dispatch implementation and private WebSocket push after commit |
| Backtesting | RSI loop could allocate forever; fixed BTC symbol; dates ignored | Finite indicator calculations, validated parameters, selected symbol/interval and bounded date filtering |
| Backtest authorization | Any authenticated caller could start another user's strategy | Ownership validation for strategy execution and run access |
| Backtest execution | Async disabled; failure semantics incomplete | Separate asynchronous worker, committed status changes, result polling, persisted recovery candidates |
| Backtest realism | Same-close fills; limited metrics | Next-bar-open fills, fees, final liquidation, equity curve and drawdown |
| WebSocket | Public handshake with unauthenticated unrestricted subscriptions | Authenticated CONNECT; restricted subscriptions and read-only client channel |
| UI | Entire Angular project absent | Login/register, protected navigation, overview, watchlist, streaming line chart, daily candles, alert manager, strategy lab and notification center |
| UI auth | No interceptor/guard/renewal | Bearer interceptor, owner session, shared refresh request, logout and guards |
| Packaging | Infrastructure only | Backend/UI Dockerfiles, Nginx proxy, Compose health dependencies, wrapper build instructions and verification script |
| Tests | Single context-load test | Backend engine/API/pipeline regression tests and Angular authentication/chart tests |

## Intentional adjustments to the original plan

- Angular 21 replaces the obsolete Angular 17 baseline and matches Node 24. Standalone components and signals provide state management; RxJS handles requests and streams. NgRx was unnecessary for this application size.
- The UI uses custom responsive styles and SVG charts instead of Angular Material and Chart.js/ng2-charts. This supports OHLC candles without requiring a separate financial-chart adapter.
- The three logical projects are co-located under a Maven reactor instead of relying on the old `e:/...` directories.
- The market provider is explicitly simulated. No exchange credentials, vendor choice or licensed data source was supplied.
- Redis caches historical responses for 60 seconds and stores latest tick snapshots for 30 seconds. User-profile caching was deliberately omitted: authentication still checks the current user record.
- Polling retrieves durable backtest results. Private result WebSocket events are also emitted, but the UI does not depend on a transient push arriving.

## Verification and boundaries

The accompanying verification report records the final command results. H2 API tests verify application behavior but not PostgreSQL migration compatibility. Embedded Kafka tests verify the messaging path without a Docker daemon. Angular tests check rendered chart output and authentication request behavior; they are not a substitute for visual browser inspection.

This machine had no Docker CLI/daemon available. The in-app browser could not attach to the local preview and timed out. Consequently, a real PostgreSQL/Redis/container smoke test and visual browser walkthrough remain operational acceptance checks. SMTP delivery requires a real configured account and has not been exercised against an external mail service.

## Production work beyond the local application

These are deployment/scale boundaries, not claims of completed production certification:

1. Run all Flyway migrations against a clean PostgreSQL 15 instance, then exercise the Compose stack and responsive UI. Validate upgrades with representative existing data; a unique tick index requires duplicate-free historical rows.
2. Replace the demo source with a licensed provider and implement corporate-action/calendar-aware history ingestion. Daily demo bars are seeded; the historical candlestick view is separate from streaming tick prices.
3. Use HTTPS, a deployment-specific WebSocket origin allowlist, secret management, database backups and an external SMTP account. Compose defaults are local-only.
4. Add rate limiting, email verification/password recovery if public signups are required, and consider HttpOnly refresh cookies with CSRF protection. Session-storage tokens remain exposed to successful XSS.
5. Kafka and PostgreSQL do not share a transaction. The outbox provides at-least-once publication. A publish-before-commit crash can repeat an event; notification deduplication handles repeated logical alerts. SMTP cannot provide exactly-once delivery across a crash without a provider idempotency contract.
6. The simple WebSocket broker is per application instance. Use a shared broker/relay and revisit Kafka fan-out before horizontal UI gateway scaling.
7. Add retention/archiving for ticks, completed runs and published outbox rows, paginated history, metrics/alerts, consumer-lag dashboards and an operational DLT replay process for long-running deployments.
8. Backtest recovery retries runs left pending or running for more than 15 minutes. That timeout is a local workload assumption; larger jobs need leases/heartbeats and a durable work queue.
9. Monetary input is decimal; the simulation engine uses double arithmetic and rounds reported values. An execution/accounting system would require exact decimal accounting and realistic lot sizing.
10. Timestamps currently use the server's local datetime representation. Standardize deployment timezone and migrate event timestamps to explicit UTC instants for multi-region ingestion.

## Suggested acceptance walkthrough

Register → open overview → receive ticks → star a symbol → create an immediately satisfiable alert → verify one notification → mark it read → run each strategy → inspect metrics/equity → sign out → verify protected routes redirect → sign in with a second user and verify isolation. Stop Kafka temporarily and confirm pending outbox rows remain; restart it and confirm eventual notification delivery.
