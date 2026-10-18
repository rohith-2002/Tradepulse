package com.tradepulse.kafka;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String MARKET_TICKS = "tradepulse.market.ticks";
    public static final String ALERT_TRIGGERED = "tradepulse.alert.triggered";
    public static final String NOTIFICATION_SEND = "tradepulse.notification.send";
    public static final String BACKTEST_RESULT = "tradepulse.backtest.result";
}
