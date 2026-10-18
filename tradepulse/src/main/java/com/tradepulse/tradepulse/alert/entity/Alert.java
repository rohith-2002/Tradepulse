package com.tradepulse.tradepulse.alert.entity;

import com.tradepulse.tradepulse.auth.entity.User;
import com.tradepulse.tradepulse.market.entity.MarketSymbol;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "symbol_id", nullable = false)
    private MarketSymbol symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private ConditionType conditionType;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal threshold;

    @Builder.Default
    @Column(name = "notification_channel", nullable = false)
    private String notificationChannel = "IN_APP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ConditionType {
        PRICE_ABOVE, PRICE_BELOW, VOLUME_ABOVE, PCT_CHANGE
    }

    public enum AlertStatus {
        ACTIVE, TRIGGERED, RESOLVED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = AlertStatus.ACTIVE;
    }
}
