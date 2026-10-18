package com.tradepulse.tradepulse.notification.repository;

import com.tradepulse.tradepulse.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventId(String eventId);
    List<Notification> findByUserIdOrderByIdDesc(Long userId);
    List<Notification> findByUserIdAndStatus(Long userId, Notification.NotificationStatus status);
}
