package com.tradepulse.tradepulse;

import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import com.tradepulse.tradepulse.backtest.entity.Strategy;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BacktestEngineTests {
    private final BacktestEngine engine=new BacktestEngine();
    private Strategy strategy(String type){return Strategy.builder().name(type).parameters(new HashMap<>(Map.of("fastPeriod",3,"slowPeriod",8,"period",5,"signalPeriod",3,"feeBps",10))).build();}
    private List<OhlcvBar> history(boolean flat){List<OhlcvBar> bars=new ArrayList<>();for(int i=0;i<180;i++){var price=BigDecimal.valueOf(flat?100:100+15*Math.sin(i/4.0));bars.add(OhlcvBar.builder().barTime(LocalDateTime.of(2025,1,1,0,0).plusDays(i)).open(price).close(price).high(price).low(price).volume(100L).build());}return bars;}
    @Test void allStrategiesFinishAndProduceFiniteEquity(){for(String type:List.of("SMA","RSI","MACD")){var result=assertTimeoutPreemptively(Duration.ofSeconds(2),()->engine.execute(strategy(type),history(false)));assertTrue(Double.isFinite((Double)result.get("finalBalance")));assertFalse(((List<?>)result.get("equityCurve")).isEmpty());assertTrue((Integer)result.get("totalTrades")>0);}}
    @Test void flatPricesDoNotGenerateProfit(){for(String type:List.of("SMA","RSI","MACD")){var result=engine.execute(strategy(type),history(true));assertEquals(0.0,result.get("netProfit"));assertEquals(0,result.get("totalTrades"));}}
    @Test void rejectsBadPeriodsAndUnknownStrategies(){var s=strategy("SMA");s.getParameters().put("fastPeriod",0);assertThrows(IllegalArgumentException.class,()->engine.execute(s,history(false)));assertThrows(IllegalArgumentException.class,()->engine.execute(strategy("UNKNOWN"),history(false)));}
    @Test void rejectsInsufficientAndUnorderedHistory(){assertThrows(IllegalArgumentException.class,()->engine.execute(strategy("RSI"),history(false).subList(0,4)));var bars=history(false);Collections.reverse(bars);assertThrows(IllegalArgumentException.class,()->engine.execute(strategy("SMA"),bars));}
    @Test void feesReduceBalance(){var s=strategy("SMA");s.getParameters().put("feeBps",0);double free=(Double)engine.execute(s,history(false)).get("finalBalance");s.getParameters().put("feeBps",50);assertTrue((Double)engine.execute(s,history(false)).get("finalBalance")<free);}
    @Test void changingFuturePricesDoesNotChangeEarlierEquity(){var bars=history(false);var original=(List<?>)engine.execute(strategy("SMA"),bars).get("equityCurve");bars.get(150).setClose(BigDecimal.valueOf(500));var changed=(List<?>)engine.execute(strategy("SMA"),bars).get("equityCurve");assertEquals(original.subList(0,100),changed.subList(0,100));}
}
