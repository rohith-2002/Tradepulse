package com.tradepulse.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickEvent {
    private String symbol;
    private BigDecimal price;
    private Long volume;
    private BigDecimal changePercent;
    private LocalDateTime timestamp;
    private java.time.LocalDate sourceDate;
    private String feedMode;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
}
