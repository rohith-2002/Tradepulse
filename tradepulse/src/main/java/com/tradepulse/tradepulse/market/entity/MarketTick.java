package com.tradepulse.tradepulse.market.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_ticks",
       indexes = @Index(name = "idx_market_ticks_symbol_ts", columnList = "symbol_id, ts DESC"))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MarketTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false)
    private MarketSymbol symbol;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private Long volume = 0L;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;
    private java.time.LocalDate sourceDate;
    private String feedMode;
    @Column(precision = 18, scale = 4)
    private BigDecimal changePercent;

    @PrePersist
    protected void onPersist() {
        if (ts == null) ts = LocalDateTime.now();
    }
}
