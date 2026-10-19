package com.tradepulse.tradepulse;

import com.fasterxml.jackson.databind.*;
import com.tradepulse.tradepulse.market.entity.*;
import com.tradepulse.tradepulse.market.repository.*;
import org.junit.jupiter.api.Test;
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
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BacktestApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MarketSymbolRepository symbols;
    @Autowired OhlcvBarRepository bars;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.tradepulse.tradepulse.market.service.HistoricalDataset dataset;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Test void replacesOldDemoCandlesAndRepeatedImportIsIdempotent() {
        var symbol=symbols.save(MarketSymbol.builder().symbol("RELIANCE").exchange("NSE").sector("Demo").active(true).build());
        bars.save(OhlcvBar.builder().symbol(symbol).interval("1d").barTime(LocalDateTime.of(2026,1,1,0,0)).open(BigDecimal.ONE).high(BigDecimal.ONE).low(BigDecimal.ONE).close(BigDecimal.ONE).volume(1L).build());
        var producer=org.mockito.Mockito.mock(com.tradepulse.kafka.producer.MarketTickProducer.class);
        var importer=new com.tradepulse.tradepulse.market.service.DemoMarketFeed(symbols,bars,producer,dataset,tx,redis);
        importer.run(null);importer.run(null);
        for(var instrument:dataset.snapshot().instruments()){
            var stored=bars.findBySymbolSymbolAndIntervalOrderByBarTimeAsc(instrument.symbol(),"1d");
            assertEquals(instrument.bars().size(),stored.size());
            assertTrue(stored.stream().allMatch(b->b.getBarTime().getYear()<2021));
        }
        assertEquals("SIMULATED_LIVE",importer.quote("RELIANCE").getFeedMode());
        assertEquals(121,importer.snapshot("RELIANCE").bars().size());
        importer.tick();
        var events=org.mockito.ArgumentCaptor.forClass(com.tradepulse.kafka.event.TickEvent.class);
        org.mockito.Mockito.verify(producer,org.mockito.Mockito.times(6)).sendTick(events.capture());
        for(var event:events.getAllValues()){
            assertTrue(event.getPrice().signum()>0);
            assertEquals("1m",event.getInterval());
            assertEquals("SIMULATED_LIVE",event.getFeedMode());
            assertEquals(event.getTimestamp().toLocalDate(),event.getSourceDate());
        }
    }

    @Test void completesAsyncRunWithinRequestedDatesAndSerializesResults()throws Exception{
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_outbox(id BIGSERIAL PRIMARY KEY,topic VARCHAR(255),event_key VARCHAR(255),event_type VARCHAR(100),payload TEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,published_at TIMESTAMP)");
        var symbol=symbols.save(MarketSymbol.builder().symbol("BACKTEST").exchange("NSE").active(true).build());
        for(int i=0;i<150;i++){var price=BigDecimal.valueOf(100+15*Math.sin(i/4.0));bars.save(OhlcvBar.builder().symbol(symbol).interval("1d").barTime(LocalDateTime.of(2025,1,1,0,0).plusDays(i)).open(price).close(price).high(price).low(price).volume(100L).build());}
        var auth=json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email",UUID.randomUUID()+"@test.com","password","Password123!")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token="Bearer "+auth.get("accessToken").asText();
        var strategy=json.readTree(mvc.perform(post("/api/backtest/strategies").header("Authorization",token).contentType("application/json").content("{\"name\":\"RSI\",\"parameters\":{\"symbol\":\"BACKTEST\",\"period\":5}}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var run=json.readTree(mvc.perform(post("/api/backtest/run").header("Authorization",token).contentType("application/json").content(json.writeValueAsString(Map.of("strategyId",strategy.get("id").asLong(),"fromDate","2025-02-01","toDate","2025-04-01")))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        long id=run.get("id").asLong();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(()->mvc.perform(get("/api/backtest/runs/"+id).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.result.equityCurve").isArray()).andExpect(jsonPath("$.strategy.user").doesNotExist()));
        var result=json.readTree(mvc.perform(get("/api/backtest/runs/"+id).header("Authorization",token)).andReturn().getResponse().getContentAsString());
        for(var point:result.get("result").get("equityCurve")){assertTrue(point.get("date").asText().compareTo("2025-02-01")>=0);assertTrue(point.get("date").asText().compareTo("2025-04-02")<0);}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE event_type='BacktestResultEvent' AND event_key=?",Integer.class,Long.toString(id)));
    }
}
