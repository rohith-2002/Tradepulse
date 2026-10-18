package com.tradepulse.kafka.handler;

import com.tradepulse.kafka.event.BacktestResultEvent;

@FunctionalInterface
public interface BacktestResultEventHandler {
    void handle(BacktestResultEvent event);
}
