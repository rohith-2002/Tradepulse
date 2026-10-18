package com.tradepulse.kafka.producer;

import com.tradepulse.kafka.KafkaTopics;
import com.tradepulse.kafka.event.BacktestResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacktestResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendResult(BacktestResultEvent event) {
        log.info("Producing backtest result event for run: {}", event.getRunId());
        kafkaTemplate.send(KafkaTopics.BACKTEST_RESULT, event.getRunId().toString(), event);
    }
}
