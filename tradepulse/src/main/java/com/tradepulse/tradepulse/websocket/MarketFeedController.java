package com.tradepulse.tradepulse.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class MarketFeedController {

    @SubscribeMapping("/market/{symbol}")
    public void onSubscribe(@DestinationVariable String symbol) {
        log.info("Client subscribed to market feed for symbol: {}", symbol);
        // Optional: return the latest cached tick from Redis here
    }

    // We don't necessarily need a @MessageMapping for incoming messages from clients
    // since market data flows server -> client, but keeping the controller for subscriptions.
}
