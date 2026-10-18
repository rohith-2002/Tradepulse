package com.tradepulse.kafka.handler;

import com.tradepulse.kafka.event.AlertTriggeredEvent;

@FunctionalInterface
public interface AlertEventHandler {
    void handle(AlertTriggeredEvent event);
}
