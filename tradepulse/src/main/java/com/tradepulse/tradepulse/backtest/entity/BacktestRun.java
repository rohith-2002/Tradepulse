package com.tradepulse.tradepulse.backtest.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "backtest_runs")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BacktestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum BacktestStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = BacktestStatus.PENDING;
    }
}
