package com.tradepulse.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendEvent {
    private String eventId;
    private Long userId;
    private String channel;
    private String message;
}
