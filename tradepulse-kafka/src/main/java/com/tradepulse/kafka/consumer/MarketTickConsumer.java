package com.tradepulse.kafka.consumer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.TickEvent;
import com.tradepulse.kafka.handler.TickEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketTickConsumer {

    private final TickEventHandler handler;

    @KafkaListener(topics = KafkaTopics.MARKET_TICKS, groupId = "${spring.kafka.consumer.group-id:tradepulse-group}")
    public void consume(TickEvent event) {
        log.debug("Consumed market tick event: {} - {}", event.getSymbol(), event.getPrice());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("Error handling market tick event", e);
            throw e;
        }
    }
}
