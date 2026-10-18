package com.tradepulse.kafka.producer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.AlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertTriggeredProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendAlert(AlertTriggeredEvent event) {
        log.info("Producing alert triggered event for alert: {}", event.getAlertId());
        kafkaTemplate.send(KafkaTopics.ALERT_TRIGGERED, event.getAlertId().toString(), event);
    }
}
