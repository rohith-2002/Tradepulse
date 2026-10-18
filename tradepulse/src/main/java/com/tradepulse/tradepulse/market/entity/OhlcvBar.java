package com.tradepulse.tradepulse.market.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ohlcv_bars",
       uniqueConstraints = @UniqueConstraint(columnNames = {"symbol_id", "interval", "bar_time"}),
       indexes = @Index(name = "idx_ohlcv_bars_symbol_interval", columnList = "symbol_id, interval, bar_time DESC"))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OhlcvBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false)
    private MarketSymbol symbol;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume = 0L;

    @Column(nullable = false, length = 10)
    private String interval;

    @Column(name = "bar_time", nullable = false)
    private LocalDateTime barTime;
}
