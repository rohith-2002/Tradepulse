package com.tradepulse.kafka.consumer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.NotificationSendEvent;
import com.tradepulse.kafka.handler.NotificationEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationEventHandler handler;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND, groupId = "${spring.kafka.consumer.group-id:tradepulse-group}")
    public void consume(NotificationSendEvent event) {
        log.info("Consumed notification send event for user: {}", event.getUserId());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("Error handling notification send event", e);
            throw e;
        }
    }
}
