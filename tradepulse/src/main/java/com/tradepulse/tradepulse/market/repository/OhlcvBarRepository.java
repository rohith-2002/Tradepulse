package com.tradepulse.tradepulse.market.repository;

import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {
    List<OhlcvBar> findBySymbolSymbolAndIntervalAndBarTimeBetweenOrderByBarTimeAsc(String symbol, String interval, java.time.LocalDateTime from, java.time.LocalDateTime to);
    List<OhlcvBar> findBySymbolSymbolAndIntervalOrderByBarTimeAsc(String symbol, String interval);
}
