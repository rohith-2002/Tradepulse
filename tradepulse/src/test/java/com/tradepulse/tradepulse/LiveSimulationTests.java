package com.tradepulse.tradepulse;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tradepulse.tradepulse.market.service.*;
import com.tradepulse.tradepulse.backtest.service.LiveStrategyService;
import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveSimulationTests {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-10-19T04:30:00Z");
        public ZoneId getZone(){return ZoneId.of("Asia/Kolkata");}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
        void advance(int seconds){now=now.plusSeconds(seconds);}
    }
    private HistoricalDataset data() throws Exception{return new HistoricalDataset(JsonMapper.builder().findAndAddModules().build());}
    @Test void continuousTicksFormCandlesWithoutLoopingHistoricalDates() throws Exception {
        var data=data();var clock=new TestClock();var market=new SimulatedMarket(data,clock,new Random(7));
        String symbol=data.snapshot().instruments().getFirst().symbol();
        var initial=market.snapshot(symbol);assertEquals(121,initial.bars().size());
        assertEquals(data.month(data.snapshot().instruments().getFirst()).getLast().close().doubleValue(),initial.quote().getPrice().doubleValue());
        Set<Object> prices=new HashSet<>();
        for(int i=0;i<125;i++){
            clock.advance(1);var events=market.advance();assertEquals(6,events.size());
            var q=market.quote(symbol);prices.add(q.getPrice());assertEquals("SIMULATED_LIVE",q.getFeedMode());
            assertEquals(LocalDate.of(2026,10,19),q.getSourceDate());assertTrue(q.getPrice().signum()>0);
            assertTrue(q.getHigh().compareTo(q.getPrice())>=0);assertTrue(q.getLow().compareTo(q.getPrice())<=0);
        }
        var snapshot=market.snapshot(symbol);assertEquals(123,snapshot.bars().size());assertTrue(prices.size()>100);
        assertEquals(initial.bars().getFirst().getClose(),snapshot.bars().getFirst().getClose());
        assertEquals(LocalDateTime.of(2026,10,19,10,2),snapshot.quote().getBarTime());
        assertEquals(0,initial.bars().getLast().getVolume()); // snapshots do not mutate with subsequent ticks
    }
    @Test void strategiesUseCompletedMinutesAndOnlyMarkOpenPositionsOnTicks() throws Exception {
        var data=data();var clock=new TestClock();var market=new SimulatedMarket(data,clock,new Random(19));
        var feed=mock(DemoMarketFeed.class);ObjectProvider<DemoMarketFeed> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(feed);var service=new LiveStrategyService(data,provider,new BacktestEngine());
        String symbol=data.snapshot().instruments().getFirst().symbol();
        when(feed.snapshot(symbol)).thenAnswer(invocation->market.snapshot(symbol));
        for(int minute=0;minute<4;minute++){
            for(String type:List.of("SMA","RSI","MACD")){
                var response=service.preview(type,Map.of("symbol",symbol));var result=(Map<String,Object>)response.get("result");
                var curve=(List<Map<String,Object>>)result.get("equityCurve");assertEquals(minute+1,curve.size());
                assertEquals(result.get("finalBalance"),curve.getLast().get("equity"));
                assertEquals(market.quote(symbol).getTimestamp(),response.get("asOf"));
            }
            clock.advance(60);market.advance();
        }
    }
    @Test void unavailableSimulationDoesNotUseHiddenHistoricalContinuation() throws Exception {
        var data=data();ObjectProvider<DemoMarketFeed> provider=mock(ObjectProvider.class);
        var service=new LiveStrategyService(data,provider,new BacktestEngine());
        assertThrows(IllegalArgumentException.class,()->service.preview("SMA",Map.of("symbol",data.snapshot().instruments().getFirst().symbol())));
    }
}
