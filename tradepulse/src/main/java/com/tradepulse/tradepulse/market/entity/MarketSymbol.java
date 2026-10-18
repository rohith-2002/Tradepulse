package com.tradepulse.tradepulse.market.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "market_symbols")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MarketSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String exchange;

    @Column(length = 100)
    private String sector;

    @Column(nullable = false)
    private Boolean active = true;
}
