-- =============================================
-- TradePulse — V1 Initial Schema
-- =============================================

-- 1. users
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. refresh_tokens
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT         NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL
);

-- 3. market_symbols
CREATE TABLE market_symbols (
    id       BIGSERIAL PRIMARY KEY,
    symbol   VARCHAR(20)  NOT NULL UNIQUE,
    exchange VARCHAR(50)  NOT NULL,
    sector   VARCHAR(100),
    active   BOOLEAN      NOT NULL DEFAULT TRUE
);

-- 4. market_ticks
CREATE TABLE market_ticks (
    id          BIGSERIAL PRIMARY KEY,
    symbol_id   BIGINT         NOT NULL REFERENCES market_symbols(id),
    price       NUMERIC(18, 4) NOT NULL,
    volume      BIGINT         NOT NULL DEFAULT 0,
    ts          TIMESTAMP      NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_market_ticks_symbol_ts ON market_ticks(symbol_id, ts DESC);

-- 5. ohlcv_bars
CREATE TABLE ohlcv_bars (
    id        BIGSERIAL PRIMARY KEY,
    symbol_id BIGINT         NOT NULL REFERENCES market_symbols(id),
    open      NUMERIC(18, 4) NOT NULL,
    high      NUMERIC(18, 4) NOT NULL,
    low       NUMERIC(18, 4) NOT NULL,
    close     NUMERIC(18, 4) NOT NULL,
    volume    BIGINT         NOT NULL DEFAULT 0,
    interval  VARCHAR(10)    NOT NULL,
    bar_time  TIMESTAMP      NOT NULL,
    UNIQUE(symbol_id, interval, bar_time)
);
CREATE INDEX idx_ohlcv_bars_symbol_interval ON ohlcv_bars(symbol_id, interval, bar_time DESC);

-- 6. alerts
CREATE TABLE alerts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol_id      BIGINT           NOT NULL REFERENCES market_symbols(id),
    condition_type VARCHAR(50)      NOT NULL CHECK (condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'VOLUME_ABOVE', 'PCT_CHANGE')),
    threshold      NUMERIC(18, 4)   NOT NULL,
    status         VARCHAR(50)      NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRIGGERED', 'RESOLVED')),
    triggered_at   TIMESTAMP,
    created_at     TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- 7. strategies
CREATE TABLE strategies (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    parameters JSONB     NOT NULL DEFAULT '{}'
);

-- 8. backtest_runs
CREATE TABLE backtest_runs (
    id          BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT          NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    from_date   DATE            NOT NULL,
    to_date     DATE            NOT NULL,
    status      VARCHAR(50)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    result      JSONB,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 9. notifications
CREATE TABLE notifications (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(50)           NOT NULL CHECK (channel IN ('EMAIL', 'WEBSOCKET', 'IN_APP')),
    message TEXT                  NOT NULL,
    sent_at TIMESTAMP,
    status  VARCHAR(50)           NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'))
);
CREATE INDEX idx_notifications_user_status ON notifications(user_id, status);
