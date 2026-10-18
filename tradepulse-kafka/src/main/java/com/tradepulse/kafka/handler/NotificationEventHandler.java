package com.tradepulse.kafka.handler;

import com.tradepulse.kafka.event.NotificationSendEvent;

@FunctionalInterface
public interface NotificationEventHandler {
    void handle(NotificationSendEvent event);
}
