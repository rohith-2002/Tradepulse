# TradePulse

TradePulse is an Angular and Spring Boot market research application with streaming prices, price alerts, notifications, and historical strategy simulations. It does not place trades. The demo replays recorded daily prices for six NSE stocks from March 2020, a high-volatility stress-test month. See [historical data and provenance](docs/HISTORICAL_DATA.md).

## Workspace

| Directory | Purpose |
|---|---|
| `tradepulse/` | Java 21 / Spring Boot 3.5.12 modular monolith |
| `tradepulse-kafka/` | Reusable event contracts, producers, consumers, topic configuration, retries and dead-letter topics |
| `tradepulse-angular/` | Angular 21 standalone UI with signals, routing, forms, REST authentication and STOMP |
| `docs/PROJECT_REVIEW.md` | Original implementation audit, changes and remaining operational checks |
| `docs/INTERVIEW_GUIDE.md` | Architecture explanation, interview questions, tradeoffs and demo script |

## Run the complete demo with Docker

Install Docker Desktop with the Compose plugin and ensure the engine is running. From this directory:

```powershell
docker compose up --build -d
```

The first build downloads Maven, npm and container dependencies. Infrastructure health checks gate backend startup. The backend imports a bundled public historical snapshot: 21 March 2020 sessions per stock plus 83 earlier sessions for indicator warm-up, followed by April as the moving-market continuation. Every three seconds it reveals the next April candle and recorded daily close/volume, looping after April ends. These are historical prices, not current quotes or future predictions.

- UI: http://localhost:4200
- API documentation: http://localhost:8080/swagger-ui.html
- Kafka UI: http://localhost:8085

Create an account using the UI. There is no shared default login. Set a price-above alert with a low positive threshold to exercise the price → alert → notification pipeline. Open Strategy lab, choose an instrument and use its default March 2020 date range to run SMA, RSI or MACD.

```powershell
docker compose logs -f backend
docker compose down
```

`down` preserves the PostgreSQL volume. Do not add `--volumes` unless you intend to delete all application data. Compose binds host ports to loopback and defaults to a local demo configuration, not an internet deployment.

## Local development

Requirements: JDK 21, Node 24, Docker Compose. Maven is provided by the existing wrapper. Set `JAVA_HOME` to your JDK 21 directory.

```powershell
docker compose up -d postgres redis zookeeper kafka kafka-ui
.\tradepulse\mvnw.cmd -f pom.xml clean install
.\tradepulse\mvnw.cmd -f tradepulse/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

In a second terminal:

```powershell
cd tradepulse-angular
npm ci
npm start
```

The Angular development proxy forwards `/api/**` and `/ws-native` to port 8080. The container UI uses Nginx for equivalent routing and SPA refresh support. There is no need to enable wildcard CORS.

## Verify

```powershell
.\tradepulse\mvnw.cmd -f pom.xml clean verify
npm ci --prefix tradepulse-angular
npm run build --prefix tradepulse-angular
npm test --prefix tradepulse-angular
```

Or run `./scripts/verify.ps1`. Backend tests use an isolated H2 database; the Kafka pipeline test starts an embedded Kafka broker. These tests do not require Docker. They do not replace verification of PostgreSQL-specific Flyway migrations or container networking. Never point test database settings at a database containing real data.

## Configuration

`application.yml` is the single source of backend defaults. `application.properties` intentionally contains no duplicate settings. `application-demo.yml` enables the seed/feed and a public development-only JWT key.

| Environment variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `WEB_ALLOWED_ORIGINS` | Comma-separated WebSocket origin allowlist; defaults to local ports 4200 and 8080 |
| `REDIS_HOST` | Redis host; cache failures fall back to the database |
| `KAFKA_BOOTSTRAP_SERVERS` | Broker addresses reachable from the application |
| `JWT_SECRET` | Base64-encoded random signing key, at least 32 bytes; required outside demo/test |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP account and credential for EMAIL channel events |
| `SPRING_PROFILES_ACTIVE` | Use `demo` for historical replay on a local instance |

Demo mode generates an in-memory signing key when `JWT_SECRET` is absent; sign in again after restarting the backend. Non-demo mode requires your own secret. The `tradepulse` database password in the local examples is a public demo default, not a private credential. Supply your own environment variables for deployment; `.env` and local credential files are excluded from Git and Docker build contexts. The fixed key under test resources is only a test fixture.

Generate a key without committing it:

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Copy `.env.example` to `.env` if you need Compose overrides. Never commit `.env`.

## API and realtime contracts

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register`, `/login`, `/refresh` | Register, authenticate and rotate refresh tokens |
| POST | `/api/auth/logout` | Authenticated refresh-token invalidation |
| GET | `/api/market/symbols` | Active symbols |
| GET | `/api/market/ticks/{symbol}` | Most recent 100 ticks, newest first |
| GET | `/api/market/ohlcv/{symbol}?interval=1d` | March 2020 daily candles in demo; recent stored bars otherwise |
| GET | `/api/market/dataset` | Replay dates, public source and measured volatility |
| GET | `/api/market/quote/{symbol}` | Current replay price, with historical-close fallback outside replay |
| GET / POST | `/api/alerts` | List/create your alerts |
| DELETE | `/api/alerts/{id}` | Remove your alert |
| GET / POST | `/api/backtest/strategies` | List/create your strategies |
| POST | `/api/backtest/run` | Persist a pending run and dispatch asynchronously; returns 202 |
| GET | `/api/backtest/runs`, `/runs/{id}` | Owner-scoped history and polling |
| GET | `/api/notifications` | Your persisted notifications |
| PATCH | `/api/notifications/{id}/read` | Mark your notification read |

STOMP connects at `/ws-native` with an `Authorization: Bearer <accessToken>` CONNECT header. Allowed subscriptions are `/topic/market/{symbol}`, `/user/queue/notifications` and `/user/queue/backtests`. Arbitrary subscriptions and client SEND frames are denied. The legacy `/ws` SockJS endpoint remains available.

The UI stores tokens in session storage and the watchlist in user-keyed local storage. Access tokens expire after 15 minutes; one shared refresh request handles concurrent API 401 responses. Logout revokes refresh tokens, not access tokens already issued.

## Data and simulation semantics

- Event volume is supplied by the feed; historical replay supplies recorded whole-day volume, not intraday tick volume.
- `PCT_CHANGE` compares the absolute percentage against the threshold. Historical replay uses the preceding trading day's recorded close as its reference.
- Price alerts use strict above/below comparisons and trigger once.
- SMA and MACD trade crossovers; RSI buys below the oversold threshold and exits above the overbought threshold.
- Signals use the previous completed bar; fills use the next bar's open. The final open position is liquidated at the last close.
- Initial capital and fees are configurable. Positions are fractional and long-only. Results include net profit, return, maximum drawdown, win rate, closed trades and an equity curve.
- Slippage, taxes and corporate actions are not modelled. Replay uses actual trading dates in the source. Historical backtests are simulations and must not be presented as live performance.

See the review and interview guide for implementation details, verified results and deployment boundaries.

The Angular persistent build cache is disabled because it stalled in this Windows workspace. Production builds and tests do not require it. It can be re-enabled in angular.json on a verified development environment.

## Troubleshooting invalid compiled classes

If a build reports `ClassFormatError: Invalid method Code length 0` for a TradePulse class, the JVM rejected the generated class file before application startup. Stop any simultaneous IDE/Maven builds and rebuild both modules from the workspace root:

```powershell
cd D:\tradepulse
.\tradepulse\mvnw.cmd -f pom.xml clean install
```

Use JDK 21. `clean` removes generated module output; `install` also refreshes the local Kafka library used when launching the backend separately. This error is different from a Kafka broker or Docker network failure. If it recurs immediately after a successful clean build, check whether an IDE compiler is writing into the same output directory.
