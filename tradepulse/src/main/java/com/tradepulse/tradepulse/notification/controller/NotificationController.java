package com.tradepulse.tradepulse.notification.controller;

import com.tradepulse.tradepulse.notification.entity.Notification;
import com.tradepulse.tradepulse.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notifications and alerts")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications")
    public List<Notification> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        return notificationService.getUserNotifications(userDetails.getUsername());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public void markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        notificationService.markAsRead(id, userDetails.getUsername());
    }
}
