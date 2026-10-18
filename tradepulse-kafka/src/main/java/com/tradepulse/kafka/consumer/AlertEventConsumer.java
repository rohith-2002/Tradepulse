package com.tradepulse.kafka.consumer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.AlertTriggeredEvent;
import com.tradepulse.kafka.handler.AlertEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventConsumer {

    private final AlertEventHandler handler;

    @KafkaListener(topics = KafkaTopics.ALERT_TRIGGERED, groupId = "${spring.kafka.consumer.group-id:tradepulse-group}")
    public void consume(AlertTriggeredEvent event) {
        log.info("Consumed alert triggered event: {}", event.getAlertId());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("Error handling alert triggered event", e);
            throw e;
        }
    }
}
