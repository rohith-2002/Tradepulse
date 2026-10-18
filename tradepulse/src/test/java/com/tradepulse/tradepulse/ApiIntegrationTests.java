package com.tradepulse.tradepulse;

import com.fasterxml.jackson.databind.*;
import com.tradepulse.tradepulse.market.entity.*;
import com.tradepulse.tradepulse.market.repository.*;
import com.tradepulse.tradepulse.alert.repository.AlertRepository;
import com.tradepulse.tradepulse.alert.service.AlertService;
import com.tradepulse.tradepulse.notification.service.NotificationService;
import com.tradepulse.kafka.event.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MarketSymbolRepository symbols;
    @Autowired OhlcvBarRepository bars;
    @Autowired JdbcTemplate jdbc;
    @Autowired AlertService alerts;
    @Autowired NotificationService notifications;
    @BeforeEach void setup(){jdbc.execute("CREATE TABLE IF NOT EXISTS event_outbox(id BIGSERIAL PRIMARY KEY,topic VARCHAR(255),event_key VARCHAR(255),event_type VARCHAR(100),payload TEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,published_at TIMESTAMP)");symbols.findBySymbol("TEST").orElseGet(()->symbols.save(MarketSymbol.builder().symbol("TEST").exchange("NSE").active(true).build()));}
    private JsonNode register()throws Exception {return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email",UUID.randomUUID()+"@test.com","password","Password123!")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    private String bearer(JsonNode user){return "Bearer "+user.get("accessToken").asText();}
    @Test void protectsPrivateEndpointsAndLogout()throws Exception{mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());mvc.perform(get("/api/alerts").header("Authorization","Bearer nonsense")).andExpect(status().isUnauthorized());}
    @Test void rotatesRefreshTokensAndRejectsReuse()throws Exception{
        var user=register();String body=json.writeValueAsString(Map.of("refreshToken",user.get("refreshToken").asText()));
        var response=mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotEquals(user.get("refreshToken").asText(),json.readTree(response).get("refreshToken").asText());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }
    @Test void alertCrudDoesNotExposeUserSecrets()throws Exception{
        var user=register();String body="{\"symbol\":\"TEST\",\"condition\":\"PRICE_ABOVE\",\"threshold\":120}";
        mvc.perform(post("/api/alerts").header("Authorization",bearer(user)).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.symbol.symbol").value("TEST"));
        mvc.perform(get("/api/alerts").header("Authorization",bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$[0].user").doesNotExist());
        mvc.perform(post("/api/alerts").header("Authorization",bearer(user)).contentType("application/json").content(body.replace("120","-1"))).andExpect(status().isBadRequest());
    }
    @Test void rejectsCrossUserBacktests()throws Exception{
        var owner=register();var other=register();
        String s=mvc.perform(post("/api/backtest/strategies").header("Authorization",bearer(owner)).contentType("application/json").content("{\"name\":\"SMA\",\"parameters\":{\"symbol\":\"TEST\"}}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(s).get("id").asLong();
        mvc.perform(post("/api/backtest/run").header("Authorization",bearer(other)).contentType("application/json").content(json.writeValueAsString(Map.of("strategyId",id,"fromDate","2025-01-01","toDate","2025-06-01")))).andExpect(status().isNotFound());
    }
    @Test void alertTriggerIsOnceAndNotificationsAreIdempotent()throws Exception{
        var user=register();var a=alerts.createAlert(user.get("email").asText(),"TEST","PCT_CHANGE",BigDecimal.valueOf(2));
        var tick=TickEvent.builder().symbol("TEST").price(BigDecimal.valueOf(130)).volume(100L).timestamp(LocalDateTime.now()).changePercent(BigDecimal.valueOf(-3)).build();
        alerts.evaluate(tick);alerts.evaluate(tick);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE payload LIKE ?",Integer.class,"%\"alertId\":"+a.getId()+",%"));
        var event=NotificationSendEvent.builder().eventId("test-"+a.getId()).userId(user.get("userId").asLong()).channel("IN_APP").message("Test alert").build();notifications.dispatch(event);notifications.dispatch(event);
        assertEquals(1,notifications.getUserNotifications(user.get("email").asText()).size());
    }
}
