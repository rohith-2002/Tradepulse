package com.tradepulse.tradepulse.alert.repository;

import com.tradepulse.tradepulse.alert.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserId(Long userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<Alert> findBySymbolSymbolAndStatus(String symbol, Alert.AlertStatus status);
}
