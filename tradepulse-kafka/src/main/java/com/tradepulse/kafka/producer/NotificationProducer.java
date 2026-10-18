package com.tradepulse.kafka.producer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.NotificationSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendNotification(NotificationSendEvent event) {
        log.info("Producing notification send event for user: {}", event.getUserId());
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_SEND, event.getUserId().toString(), event);
    }
}
