package com.tradepulse.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultEvent {
    private Long runId;
    private Long strategyId;
    private Long userId;
    private String status;
    private Map<String, Object> metrics;
    private LocalDateTime finishedAt;
}
