package com.tradepulse.tradepulse.alert.service;

import com.tradepulse.kafka.event.TickEvent;
import com.tradepulse.kafka.event.AlertTriggeredEvent;
import com.tradepulse.kafka.producer.AlertTriggeredProducer;
import com.tradepulse.tradepulse.alert.entity.Alert;
import com.tradepulse.tradepulse.alert.repository.AlertRepository;
import com.tradepulse.tradepulse.auth.repository.UserRepository;
import com.tradepulse.tradepulse.market.repository.MarketSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final MarketSymbolRepository symbolRepository;
    private final com.tradepulse.tradepulse.events.EventOutbox outbox;

    public List<Alert> getUserAlerts(String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        return alertRepository.findByUserId(user.getId());
    }

    @Transactional
    public Alert createAlert(String email, String symbol, String condition, BigDecimal threshold) {
        return createAlert(email, symbol, condition, threshold, "IN_APP");
    }

    @Transactional
    public Alert createAlert(String email, String symbol, String condition, BigDecimal threshold, String channel) {
        if (threshold == null || threshold.signum() <= 0) throw new IllegalArgumentException("Threshold must be positive");
        if (channel == null) channel = "IN_APP";
        if (!java.util.Set.of("IN_APP", "WEBSOCKET", "EMAIL").contains(channel)) throw new IllegalArgumentException("Unsupported notification channel");
        var user = userRepository.findByEmail(email).orElseThrow();
        var marketSymbol = symbolRepository.findBySymbol(symbol).orElseThrow();
        Alert alert = Alert.builder()
                .user(user)
                .symbol(marketSymbol)
                .conditionType(Alert.ConditionType.valueOf(condition))
                .threshold(threshold)
                .notificationChannel(channel)
                .status(Alert.AlertStatus.ACTIVE)
                .build();
        return alertRepository.save(alert);
    }

    @Transactional
    public void deleteAlert(Long id, String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        alertRepository.findById(id).ifPresent(alert -> {
            if (alert.getUser().getId().equals(user.getId())) {
                alertRepository.delete(alert);
            }
        });
    }

    @Transactional
    public void evaluate(TickEvent event) {
        List<Alert> activeAlerts = alertRepository.findBySymbolSymbolAndStatus(event.getSymbol(), Alert.AlertStatus.ACTIVE);
        for (Alert alert : activeAlerts) {
            boolean triggered = false;
            if (alert.getConditionType() == Alert.ConditionType.PRICE_ABOVE && event.getPrice().compareTo(alert.getThreshold()) > 0) {
                triggered = true;
            } else if (alert.getConditionType() == Alert.ConditionType.PRICE_BELOW && event.getPrice().compareTo(alert.getThreshold()) < 0) {
                triggered = true;
            } else if (alert.getConditionType() == Alert.ConditionType.VOLUME_ABOVE && event.getVolume().compareTo(alert.getThreshold().longValue()) > 0) {
                triggered = true;
            }
            if (alert.getConditionType() == Alert.ConditionType.PCT_CHANGE && event.getChangePercent() != null && event.getChangePercent().abs().compareTo(alert.getThreshold()) >= 0) triggered = true;
            if (triggered) {
                log.info("Alert triggered for symbol {} at price {}", event.getSymbol(), event.getPrice());
                alert.setStatus(Alert.AlertStatus.TRIGGERED);
                alert.setTriggeredAt(LocalDateTime.now());
                alertRepository.save(alert);

                // Produce an AlertTriggeredEvent to Kafka
                outbox.enqueue(com.tradepulse.kafka.KafkaTopics.ALERT_TRIGGERED, alert.getUser().getId().toString(), AlertTriggeredEvent.builder()
                        .alertId(alert.getId())
                        .userId(alert.getUser().getId())
                        .symbol(event.getSymbol())
                        .priceTrigger(event.getPrice())
                        .channel(alert.getNotificationChannel())
                        .build());
            }
        }
    }
}
