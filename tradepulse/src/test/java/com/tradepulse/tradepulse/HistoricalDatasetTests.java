package com.tradepulse.tradepulse;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tradepulse.tradepulse.market.service.HistoricalDataset;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import com.tradepulse.tradepulse.backtest.entity.Strategy;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalDatasetTests {
    private HistoricalDataset data() throws Exception {return new HistoricalDataset(JsonMapper.builder().findAndAddModules().build());}
    @Test void snapshotHasSixCompleteMatchingTradingCalendarsAndMeasuredVolatility() throws Exception {
        var data=data();assertEquals(6,data.snapshot().instruments().size());
        var dates=data.month(data.snapshot().instruments().getFirst()).stream().map(HistoricalDataset.Candle::date).toList();
        assertEquals(21,dates.size());
        for(var instrument:data.snapshot().instruments()){
            var month=data.month(instrument);
            assertEquals(dates,month.stream().map(HistoricalDataset.Candle::date).toList());
            var continuation=data.continuation(instrument);
            assertTrue(continuation.size()>=18);
            assertTrue(continuation.getFirst().date().isAfter(data.snapshot().to()));
            assertEquals(data.continuation(data.snapshot().instruments().getFirst()).stream().map(HistoricalDataset.Candle::date).toList(),continuation.stream().map(HistoricalDataset.Candle::date).toList());
            assertTrue(instrument.sha256().matches("[a-f0-9]{64}"));
            double[] returns=new double[month.size()-1];
            for(int i=1;i<month.size();i++)returns[i-1]=Math.log(month.get(i).close().doubleValue()/month.get(i-1).close().doubleValue());
            double mean=Arrays.stream(returns).average().orElseThrow();
            double vol=Math.sqrt(Arrays.stream(returns).map(r->Math.pow(r-mean,2)).sum()/(returns.length-1))*100;
            assertEquals(vol,instrument.dailyVolatilityPercent(),0.01);
            assertTrue(vol>3,"Stress sample must have high observed daily volatility");
        }
    }
    @Test void allDefaultStrategiesTradeOnlyInsideMarchWithRealWarmup() throws Exception {
        var data=data();var engine=new BacktestEngine();
        for(var instrument:data.snapshot().instruments()){
            var bars=instrument.bars().stream().filter(c->!c.date().isAfter(data.snapshot().to())).map(c->OhlcvBar.builder().barTime(c.date().atStartOfDay()).open(c.open()).high(c.high()).low(c.low()).close(c.close()).volume(c.volume()).build()).toList();
            for(String type:List.of("SMA","RSI","MACD")){
                var strategy=Strategy.builder().name(type).parameters(new HashMap<>()).build();
                var result=engine.execute(strategy,bars,LocalDate.of(2020,3,1));
                var curve=(List<Map<String,Object>>)result.get("equityCurve");
                assertEquals(21,curve.size());
                assertTrue(curve.stream().allMatch(p->p.get("date").toString().startsWith("2020-03-")));
                assertTrue(Double.isFinite((Double)result.get("finalBalance")));
                assertThrows(IllegalArgumentException.class,()->engine.execute(strategy,bars.subList(bars.size()-21,bars.size()),LocalDate.of(2020,3,1)));
            }
        }
    }
}
