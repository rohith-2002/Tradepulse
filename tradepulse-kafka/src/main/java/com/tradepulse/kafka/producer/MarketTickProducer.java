package com.tradepulse.kafka.producer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.TickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketTickProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTick(TickEvent event) {
        log.debug("Producing market tick: {} - {}", event.getSymbol(), event.getPrice());
        kafkaTemplate.send(KafkaTopics.MARKET_TICKS, event.getSymbol(), event);
    }
}
