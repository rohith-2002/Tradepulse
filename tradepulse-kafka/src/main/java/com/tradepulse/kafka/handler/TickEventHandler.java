package com.tradepulse.kafka.handler;

import com.tradepulse.kafka.event.TickEvent;

@FunctionalInterface
public interface TickEventHandler {
    void handle(TickEvent event);
}
