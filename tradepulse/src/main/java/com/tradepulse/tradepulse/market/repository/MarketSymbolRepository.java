package com.tradepulse.tradepulse.market.repository;

import com.tradepulse.tradepulse.market.entity.MarketSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketSymbolRepository extends JpaRepository<MarketSymbol, Long> {
    Optional<MarketSymbol> findBySymbol(String symbol);
}
