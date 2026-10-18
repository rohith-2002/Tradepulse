package com.tradepulse.tradepulse.websocket;

import com.tradepulse.kafka.event.TickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketFeedBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(TickEvent event) {
        String destination = "/topic/market/" + event.getSymbol();
        messagingTemplate.convertAndSend(destination, event);
    }
}
