package com.tradepulse.tradepulse.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepulse.kafka.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="tradepulse.outbox.enabled",havingValue="true",matchIfMissing=true)
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String,Object> kafka;
    private final ObjectMapper json;
    private static final Map<String,Class<?>> TYPES=Map.of("AlertTriggeredEvent",AlertTriggeredEvent.class,"NotificationSendEvent",NotificationSendEvent.class,"BacktestResultEvent",BacktestResultEvent.class);
    @Scheduled(fixedDelay=2000)
    @Transactional
    public void publish(){
        var rows=jdbc.queryForList("SELECT id, topic, event_key, event_type, payload FROM event_outbox WHERE published_at IS NULL ORDER BY id LIMIT 25 FOR UPDATE SKIP LOCKED");
        for(var row:rows){
            try{
                var type=TYPES.get(row.get("event_type").toString());
                if(type==null)throw new IllegalArgumentException("Unknown event type");
                kafka.send(row.get("topic").toString(),row.get("event_key").toString(),json.readValue(row.get("payload").toString(),type)).get(5,TimeUnit.SECONDS);
                jdbc.update("UPDATE event_outbox SET published_at=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));
            }catch(Exception ex){log.warn("Outbox event {} retained for retry: {}",row.get("id"),ex.getClass().getSimpleName());if(ex instanceof InterruptedException)Thread.currentThread().interrupt();break;}
        }
    }
}
