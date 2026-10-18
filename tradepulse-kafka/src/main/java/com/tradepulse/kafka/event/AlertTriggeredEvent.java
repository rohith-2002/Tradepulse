package com.tradepulse.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertTriggeredEvent {
    private Long alertId;
    private Long userId;
    private String symbol;
    private BigDecimal priceTrigger;
    private String channel;
}
