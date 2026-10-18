package com.tradepulse.kafka.config;

import com.tradepulse.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic marketTicksTopic() {
        return TopicBuilder.name(KafkaTopics.MARKET_TICKS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertTriggeredTopic() {
        return TopicBuilder.name(KafkaTopics.ALERT_TRIGGERED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationSendTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_SEND)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic backtestResultTopic() {
        return TopicBuilder.name(KafkaTopics.BACKTEST_RESULT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
