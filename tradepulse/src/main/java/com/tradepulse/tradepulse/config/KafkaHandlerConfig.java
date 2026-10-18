package com.tradepulse.tradepulse.config;

import com.tradepulse.kafka.event.AlertTriggeredEvent;
import com.tradepulse.kafka.event.BacktestResultEvent;
import com.tradepulse.kafka.event.NotificationSendEvent;
import com.tradepulse.kafka.event.TickEvent;
import com.tradepulse.kafka.handler.AlertEventHandler;
import com.tradepulse.kafka.handler.BacktestResultEventHandler;
import com.tradepulse.kafka.handler.NotificationEventHandler;
import com.tradepulse.kafka.handler.TickEventHandler;
import com.tradepulse.tradepulse.alert.service.AlertService;
import com.tradepulse.tradepulse.notification.service.NotificationService;
import com.tradepulse.tradepulse.websocket.MarketFeedBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
@Slf4j
public class KafkaHandlerConfig {

    @Bean
    public TickEventHandler tickEventHandler(AlertService alertService, MarketFeedBroadcaster ws, com.tradepulse.tradepulse.market.service.MarketDataService market) {
        return event -> {
            log.trace("Received tick event: {} - {}", event.getSymbol(), event.getPrice());
            market.ingest(event);
            alertService.evaluate(event);
            // Broadcast via WebSocket
            ws.broadcast(event);
        };
    }

    @Bean
    public NotificationEventHandler notificationEventHandler(NotificationService notificationService) {
        return event -> {
            log.info("Received notification event for user {}", event.getUserId());
            notificationService.dispatch(event);
        };
    }

    @Bean
    public AlertEventHandler alertEventHandler(com.tradepulse.tradepulse.events.EventOutbox outbox, org.springframework.transaction.support.TransactionTemplate tx) {
        return event -> {
            log.info("Alert triggered event handled in monolith for user {} on {}", event.getUserId(), event.getSymbol());
            NotificationSendEvent notificationEvent = NotificationSendEvent.builder()
                    .eventId("alert-" + event.getAlertId())
                    .userId(event.getUserId())
                    .channel(event.getChannel() == null ? "IN_APP" : event.getChannel())
                    .message(String.format("Alert triggered for %s at price %s", event.getSymbol(), event.getPriceTrigger()))
                    .build();
            tx.executeWithoutResult(s -> outbox.enqueue(com.tradepulse.kafka.KafkaTopics.NOTIFICATION_SEND, event.getUserId().toString(), notificationEvent));
        };
    }

    @Bean
    public BacktestResultEventHandler backtestResultEventHandler(SimpMessagingTemplate messagingTemplate, com.tradepulse.tradepulse.auth.repository.UserRepository users) {
        return event -> {
            log.info("Backtest result event handled in monolith for run {}", event.getRunId());
            if(event.getUserId()!=null)users.findById(event.getUserId()).ifPresent(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/backtests", event));
        };
    }
}
