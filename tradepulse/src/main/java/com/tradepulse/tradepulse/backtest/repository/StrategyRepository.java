package com.tradepulse.tradepulse.backtest.repository;

import com.tradepulse.tradepulse.backtest.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {
    List<Strategy> findByUserId(Long userId);
}
