package com.tradepulse.tradepulse.notification.entity;

import com.tradepulse.tradepulse.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
       indexes = @Index(name = "idx_notifications_user_status", columnList = "user_id, status"))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="event_id", unique=true)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    public enum NotificationChannel {
        EMAIL, WEBSOCKET, IN_APP
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED, READ
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) status = NotificationStatus.PENDING;
    }
}
