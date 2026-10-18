package com.tradepulse.tradepulse.market.repository;

import com.tradepulse.tradepulse.market.entity.MarketTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketTickRepository extends JpaRepository<MarketTick, Long> {
    boolean existsBySymbolSymbolAndTs(String symbol, java.time.LocalDateTime ts);
    List<MarketTick> findTop100BySymbolSymbolOrderByTsDesc(String symbol);
}
