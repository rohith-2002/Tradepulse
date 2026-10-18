# TradePulse — Three-Repo Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        TradePulse System                        │
│                                                                 │
│  ┌──────────────────┐    ┌───────────────────────────────────┐  │
│  │ tradepulse-kafka │    │          tradepulse               │  │
│  │  (Maven JAR lib) │◄───│     (Spring Boot Monolith)        │  │
│  │                  │    │                                   │  │
│  │  • Topics        │    │  • Auth Module (JWT)              │  │
│  │  • Event Schemas │    │  • Market Data Module             │  │
│  │  • Producers     │    │  • Alert Engine                   │  │
│  │  • Consumers     │    │  • Strategy Backtesting           │  │
│  │  • Auto-Config   │    │  • Notification Module            │  │
│  └──────────────────┘    │  • WebSocket Gateway              │  │
│                          │  • Redis Cache                    │  │
│  ┌──────────────────┐    │  • PostgreSQL (Flyway)            │  │
│  │tradepulse-angular│    └───────────────────────────────────┘  │
│  │  (Angular 17 SPA)│◄── REST + WebSocket (STOMP/SockJS)        │
│  │                  │                                           │
│  │  • Dashboard     │    Infrastructure (Docker Compose)        │
│  │  • Live Charts   │    • PostgreSQL  • Redis                  │
│  │  • Alert Manager │    • Kafka       • Zookeeper              │
│  │  • Backtesting   │    • Kafka UI                             │
│  └──────────────────┘                                           │
└─────────────────────────────────────────────────────────────────┘
```

| Repo | Type | Location |
|---|---|---|
| `tradepulse-kafka` | Maven JAR library | `e:/tradepulse-kafka` |
| `tradepulse` | Spring Boot monolith | `e:/tradepulse` |
| `tradepulse-angular` | Angular 17 SPA | `e:/tradepulse-angular` |

---

## Repo 1 — `tradepulse-kafka` (Kafka Infrastructure Library)

> Plain Maven JAR. No Spring Boot plugin. Installed locally via `mvn install` and imported by monolith.

### Structure
```
tradepulse-kafka/
├── pom.xml
└── src/main/java/com/tradepulse/kafka/
    ├── KafkaTopics.java              ← topic name constants
    ├── config/
    │   └── KafkaTopicConfig.java     ← NewTopic beans
    ├── event/
    │   ├── TickEvent.java
    │   ├── AlertTriggeredEvent.java
    │   ├── NotificationSendEvent.java
    │   └── BacktestResultEvent.java
    ├── handler/
    │   ├── TickEventHandler.java     ← @FunctionalInterface
    │   ├── AlertEventHandler.java
    │   └── NotificationEventHandler.java
    ├── producer/
    │   ├── MarketTickProducer.java
    │   ├── AlertTriggeredProducer.java
    │   ├── NotificationProducer.java
    │   └── BacktestResultProducer.java
    ├── consumer/
    │   ├── MarketTickConsumer.java
    │   ├── AlertEventConsumer.java
    │   └── NotificationEventConsumer.java
    └── autoconfigure/
        └── TradePulseKafkaAutoConfiguration.java
```

### Key Design
- **Topics**: `tradepulse.market.ticks`, `tradepulse.alert.triggered`, `tradepulse.notification.send`, `tradepulse.backtest.result`
- **Consumers** hold `@KafkaListener` + retry/DLQ logic. They accept injected **handler functional interfaces** — zero business logic inside this lib
- **Auto-configuration** via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## Repo 2 — `tradepulse` (Spring Boot Monolith)

### Initializr Dependencies
From [start.spring.io](https://start.spring.io): `Spring Web`, `Spring Security`, `Spring Data JPA`, `Spring Data Redis`, `WebSocket`, `Spring Mail`, `Validation`, `Flyway`, `PostgreSQL Driver`, `Lombok`, `DevTools`

Manual additions in `pom.xml`: `jjwt` (0.12.x), `springdoc-openapi` (2.4.x), `tradepulse-kafka` (1.0.0 local)

### Structure
```
tradepulse/
├── pom.xml
├── docker-compose.yml
└── src/main/
    ├── resources/
    │   ├── application.yml
    │   └── db/migration/V1__init_schema.sql
    └── java/com/tradepulse/
        ├── TradePulseApplication.java
        ├── config/
        │   ├── SecurityConfig.java
        │   ├── RedisConfig.java
        │   ├── WebSocketConfig.java
        │   └── KafkaHandlerConfig.java   ← wires business handlers to Kafka consumers
        ├── auth/
        │   ├── entity/User.java, RefreshToken.java
        │   ├── repository/
        │   ├── service/AuthService.java, JwtService.java
        │   ├── filter/JwtAuthFilter.java
        │   ├── controller/AuthController.java
        │   └── dto/
        ├── market/
        │   ├── entity/MarketSymbol.java, MarketTick.java, OhlcvBar.java
        │   ├── repository/
        │   ├── service/MarketDataService.java
        │   └── controller/MarketDataController.java
        ├── alert/
        │   ├── entity/Alert.java          ← conditions: PRICE_ABOVE/BELOW, VOLUME_ABOVE, PCT_CHANGE
        │   ├── repository/
        │   ├── service/AlertService.java
        │   └── controller/AlertController.java
        ├── backtest/
        │   ├── entity/Strategy.java, BacktestRun.java
        │   ├── engine/BacktestEngine.java ← SMA, RSI, MACD strategies
        │   ├── service/BacktestService.java
        │   └── controller/BacktestController.java
        ├── notification/
        │   ├── entity/Notification.java   ← channels: EMAIL, WEBSOCKET, IN_APP
        │   ├── service/NotificationService.java
        │   └── controller/NotificationController.java
        └── websocket/
            └── MarketFeedController.java  ← @MessageMapping → /topic/market/{symbol}
```

### Database Schema (Flyway V1)
| Table | Key Columns |
|---|---|
| `users` | id, email, password_hash, role, created_at |
| `refresh_tokens` | id, user_id, token, expires_at |
| `market_symbols` | id, symbol, exchange, sector, active |
| `market_ticks` | id, symbol_id, price, volume, timestamp |
| `ohlcv_bars` | id, symbol_id, open, high, low, close, volume, interval, bar_time |
| `alerts` | id, user_id, symbol_id, condition_type, threshold, status, triggered_at |
| `strategies` | id, user_id, name, parameters JSONB |
| `backtest_runs` | id, strategy_id, from_date, to_date, status, result JSONB |
| `notifications` | id, user_id, channel, message, sent_at, status |

### REST API Surface
| Module | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` |
| Market | `GET /api/market/symbols`, `/ticks/{symbol}`, `/ohlcv/{symbol}` |
| Alert | `GET/POST /api/alerts`, `DELETE /api/alerts/{id}` |
| Backtest | `POST /api/backtest/run`, `GET /api/backtest/runs/{id}` |
| Notifications | `GET /api/notifications`, `PATCH /api/notifications/{id}/read` |

### Redis Caching Strategy
| Key | TTL | Content |
|---|---|---|
| `market:price:{symbol}` | 30s | latest tick price |
| `market:ohlcv:{symbol}:{interval}` | 60s | OHLCV bar list |
| `user:profile:{userId}` | 10m | user details |

### Kafka Handler Wiring (`KafkaHandlerConfig.java`)
```java
// Monolith injects business logic into Kafka lib consumers
@Bean TickEventHandler tickHandler(AlertService a, MarketFeedBroadcaster ws) {
    return event -> { a.evaluate(event); ws.broadcast(event); };
}
@Bean AlertEventHandler alertHandler(NotificationService n) {
    return n::dispatch;
}
```

---

## Repo 3 — `tradepulse-angular` (Angular 17 SPA)

### Tech Stack
- Angular 17 (standalone components)
- `@stomp/rx-stomp` — STOMP WebSocket client
- `chart.js` + `ng2-charts` — live candlestick & line charts
- `@ngrx/store` — state management
- Angular Material — UI component library

### Module Structure
```
tradepulse-angular/
├── src/app/
│   ├── core/
│   │   ├── auth/          ← JWT interceptor, auth guard, login/register pages
│   │   ├── services/      ← ApiService, WebSocketService, AuthService
│   │   └── store/         ← NgRx state (auth, market, alerts)
│   ├── features/
│   │   ├── dashboard/     ← Market overview, watchlist, live price ticker
│   │   ├── market/        ← Symbol detail, live OHLCV candlestick chart
│   │   ├── alerts/        ← Create/manage price alerts, alert history
│   │   ├── backtest/      ← Strategy selector, parameter config, results view
│   │   └── notifications/ ← Notification center
│   └── shared/
│       └── components/    ← Chart component, price badge, sidebar, navbar
```

### Key Angular Features
- **Live Ticks** — WebSocket subscription to `/topic/market/{symbol}` updates chart in real-time
- **JWT Handling** — HTTP interceptor attaches Bearer token; refresh token auto-renew
- **Alert Creation** — Form with symbol search, condition dropdown, threshold input
- **Backtest UI** — Select strategy + date range → POST to API → poll result → display metrics table + equity curve chart

---

## Verification Plan

### Step 1 — Build Kafka Lib
```bash
cd e:/tradepulse-kafka && mvn clean install
```

### Step 2 — Start Infrastructure
```bash
cd e:/tradepulse && docker compose up -d
```

### Step 3 — Run Monolith
```bash
cd e:/tradepulse && mvn spring-boot:run
```
Verify: `http://localhost:8080/swagger-ui.html`

### Step 4 — Run Angular
```bash
cd e:/tradepulse-angular && ng serve
```
Verify: `http://localhost:4200`

---

## Implementation review — 17 October 2026

The original plan above is retained as the baseline. The working directories are now beneath `D:/tradepulse`, including the new `tradepulse-angular` project. See:

- [README](README.md) for current build/start commands.
- [Project review](docs/PROJECT_REVIEW.md) for the original gaps, completed changes, intentional architecture adjustments and remaining operational checks.
- [Verification](docs/VERIFICATION.md) for actual build/test outcomes and checks not yet run.
- [Interview guide](docs/INTERVIEW_GUIDE.md) for architecture, tradeoffs, debugging stories and a demo walkthrough.

The implemented UI uses Angular 21, standalone components, signals, RxJS and SVG charts. The backend adds transactional outbox publication, ownership checks, authenticated STOMP and async backtest recovery beyond the initial sketch. Market data is explicitly synthetic in the demo profile. Real-provider integration and production deployment validation are separate from the local demo scope.
