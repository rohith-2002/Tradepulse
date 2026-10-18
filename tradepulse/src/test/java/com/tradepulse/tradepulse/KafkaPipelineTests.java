package com.tradepulse.tradepulse;

import com.tradepulse.kafka.event.TickEvent;
import com.tradepulse.kafka.producer.MarketTickProducer;
import com.tradepulse.tradepulse.auth.dto.RegisterRequest;
import com.tradepulse.tradepulse.auth.service.AuthService;
import com.tradepulse.tradepulse.alert.service.AlertService;
import com.tradepulse.tradepulse.market.entity.MarketSymbol;
import com.tradepulse.tradepulse.market.repository.*;
import com.tradepulse.tradepulse.notification.service.NotificationService;
import com.tradepulse.tradepulse.events.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.kafka.listener.auto-startup=true","spring.kafka.admin.auto-create=true","spring.datasource.url=jdbc:h2:mem:kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL","logging.level.org.apache.kafka=ERROR","logging.level.kafka=ERROR"})
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions=3,topics={"tradepulse.market.ticks","tradepulse.alert.triggered","tradepulse.notification.send","tradepulse.backtest.result"})
class KafkaPipelineTests {
    @Autowired AuthService auth;
    @Autowired AlertService alerts;
    @Autowired NotificationService notifications;
    @Autowired MarketSymbolRepository symbols;
    @Autowired MarketTickRepository ticks;
    @Autowired MarketTickProducer producer;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired KafkaTemplate<String,Object> kafka;
    @Autowired ObjectMapper json;
    @Test void marketTickTravelsThroughKafkaOutboxAndNotificationConsumers(){
        jdbc.execute("CREATE TABLE event_outbox(id BIGSERIAL PRIMARY KEY,topic VARCHAR(255),event_key VARCHAR(255),event_type VARCHAR(100),payload TEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,published_at TIMESTAMP)");
        var user=auth.register(new RegisterRequest("pipeline@test.com","Password123!"));
        symbols.save(MarketSymbol.builder().symbol("PIPELINE").exchange("NSE").active(true).build());
        alerts.createAlert(user.email(),"PIPELINE","PRICE_ABOVE",BigDecimal.valueOf(100));
        producer.sendTick(TickEvent.builder().symbol("PIPELINE").price(BigDecimal.valueOf(110)).volume(1000L).timestamp(LocalDateTime.now()).build());
        var publisher=new OutboxPublisher(jdbc,kafka,json);
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(500)).untilAsserted(()->{
            tx.executeWithoutResult(s->publisher.publish());
            assertEquals(1,notifications.getUserNotifications(user.email()).size());
        });
        assertEquals(1,ticks.findTop100BySymbolSymbolOrderByTsDesc("PIPELINE").size());
        assertEquals("TRIGGERED",alerts.getUserAlerts(user.email()).getFirst().getStatus().name());
    }
}
