package com.tradepulse.tradepulse.notification.service;

import com.tradepulse.kafka.event.NotificationSendEvent;
import com.tradepulse.tradepulse.auth.repository.UserRepository;
import com.tradepulse.tradepulse.notification.entity.Notification;
import com.tradepulse.tradepulse.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;
    private final JavaMailSender mail;
    @Value("${spring.mail.username}") private String sender;
    public List<Notification> getUserNotifications(String email){return notifications.findByUserIdOrderByIdDesc(users.findByEmail(email).orElseThrow().getId());}
    @Transactional
    public void markAsRead(Long id,String email){
        var n=notifications.findById(id).orElseThrow();
        if(!n.getUser().getEmail().equals(email))throw new NoSuchElementException();
        n.setStatus(Notification.NotificationStatus.READ);
    }
    @Transactional
    public void dispatch(NotificationSendEvent event){
        var user=users.findLockedById(event.getUserId()).orElseThrow();
        if(event.getEventId()!=null && notifications.existsByEventId(event.getEventId()))return;
        var channel=Notification.NotificationChannel.valueOf(event.getChannel());
        var n=Notification.builder().eventId(event.getEventId()).user(user).channel(channel).message(event.getMessage()).status(Notification.NotificationStatus.SENT).sentAt(LocalDateTime.now()).build();
        if(channel==Notification.NotificationChannel.EMAIL){
            SimpleMailMessage message=new SimpleMailMessage();message.setFrom(sender);message.setTo(user.getEmail());message.setSubject("TradePulse alert");message.setText(event.getMessage());mail.send(message);
        }
        notifications.save(n);
        if(channel!=Notification.NotificationChannel.EMAIL){
            // Only notify an authenticated user's private queue after the database commit succeeds.
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){messaging.convertAndSendToUser(user.getEmail(),"/queue/notifications",event.getMessage());}
            });
        }
    }
}
