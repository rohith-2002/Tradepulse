package com.tradepulse.kafka.config;

import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import com.tradepulse.kafka.KafkaTopics;

@Configuration
public class KafkaReliabilityConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template);
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3));
    }
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics() {
        return new KafkaAdmin.NewTopics(java.util.stream.Stream.of(KafkaTopics.MARKET_TICKS, KafkaTopics.ALERT_TRIGGERED,
            KafkaTopics.NOTIFICATION_SEND, KafkaTopics.BACKTEST_RESULT)
            .map(t -> TopicBuilder.name(t + ".DLT").partitions(3).replicas(1).build())
            .toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
    }
}
