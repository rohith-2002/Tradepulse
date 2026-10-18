package com.tradepulse.tradepulse.backtest.repository;

import com.tradepulse.tradepulse.backtest.entity.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from BacktestRun r where r.id = :id")
    java.util.Optional<BacktestRun> findLockedById(Long id);
    List<BacktestRun> findTop20ByStatusOrderByIdAsc(BacktestRun.BacktestStatus status);
    List<BacktestRun> findTop20ByStatusAndStartedAtBeforeOrderByIdAsc(BacktestRun.BacktestStatus status, java.time.LocalDateTime before);
    List<BacktestRun> findByStrategyUserId(Long userId);
}
