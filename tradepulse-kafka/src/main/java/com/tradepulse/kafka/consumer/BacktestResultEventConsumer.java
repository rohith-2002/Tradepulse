package com.tradepulse.kafka.consumer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.BacktestResultEvent;
import com.tradepulse.kafka.handler.BacktestResultEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacktestResultEventConsumer {

    private final BacktestResultEventHandler handler;

    @KafkaListener(topics = KafkaTopics.BACKTEST_RESULT, groupId = "${spring.kafka.consumer.group-id:tradepulse-group}")
    public void consume(BacktestResultEvent event) {
        log.info("Consumed backtest result event for run: {}", event.getRunId());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("Error handling backtest result event", e);
            throw e;
        }
    }
}
