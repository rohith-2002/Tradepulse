package com.tradepulse.tradepulse.market.service;

import com.tradepulse.kafka.event.TickEvent;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Synthetic intraday paths calibrated only from the visible historical month. */
public class SimulatedMarket {
    public static final int WARMUP_BARS=120;
    public record Snapshot(TickEvent quote, List<OhlcvBar> bars, LocalDateTime startedAt) {}
    private static final double SESSION_SECONDS=375*60;
    private final Clock clock;
    private final Random random;
    private final Map<String,State> states=new LinkedHashMap<>();
    private final LocalDateTime startedAt;
    private static class State {
        String symbol; double[] returns; double mean; double reference; double price; double volumePerSecond;
        LocalDateTime lastTick; TickEvent quote; final List<OhlcvBar> bars=new ArrayList<>();
    }
    public SimulatedMarket(HistoricalDataset dataset, Clock clock, Random random){
        this.clock=clock;this.random=random;startedAt=LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        for(var instrument:dataset.snapshot().instruments()){
            var month=dataset.month(instrument);var s=new State();s.symbol=instrument.symbol();
            s.reference=month.getLast().close().doubleValue();s.price=s.reference;
            s.returns=new double[month.size()-1];
            for(int i=1;i<month.size();i++)s.returns[i-1]=Math.log(month.get(i).close().doubleValue()/month.get(i-1).close().doubleValue());
            s.mean=Arrays.stream(s.returns).average().orElse(0);
            s.volumePerSecond=month.stream().mapToLong(HistoricalDataset.Candle::volume).average().orElse(0)/SESSION_SECONDS;
            // These are explicitly synthetic warm-up minutes, not historical intraday observations.
            double[] prices=new double[WARMUP_BARS+1];prices[WARMUP_BARS]=s.reference;
            for(int i=WARMUP_BARS-1;i>=0;i--)prices[i]=prices[i+1]/Math.exp(shock(s,60));
            for(int i=0;i<WARMUP_BARS;i++)s.bars.add(bar(startedAt.minusMinutes(WARMUP_BARS-i),prices[i],prices[i+1],Math.round(s.volumePerSecond*60)));
            s.bars.add(bar(startedAt,s.price,s.price,0));s.lastTick=LocalDateTime.now(clock);s.quote=event(s,s.lastTick);
            states.put(s.symbol,s);
        }
    }
    private double shock(State s,double seconds){return (s.returns[random.nextInt(s.returns.length)]-s.mean)*Math.sqrt(seconds/SESSION_SECONDS);}
    private static BigDecimal money(double n){return BigDecimal.valueOf(Math.max(0.01,n)).setScale(4,RoundingMode.HALF_UP);}
    private static OhlcvBar bar(LocalDateTime time,double open,double close,long volume){return OhlcvBar.builder().interval("1m").barTime(time).open(money(open)).high(money(Math.max(open,close))).low(money(Math.min(open,close))).close(money(close)).volume(volume).build();}
    public synchronized List<TickEvent> advance(){
        var now=LocalDateTime.now(clock);var events=new ArrayList<TickEvent>();
        for(var s:states.values()){
            if(!now.isAfter(s.lastTick))continue;
            double seconds=Math.min(5,Duration.between(s.lastTick,now).toMillis()/1000.0);
            var minute=now.truncatedTo(ChronoUnit.MINUTES);
            var b=s.bars.getLast();if(minute.isAfter(b.getBarTime())){b=bar(minute,s.price,s.price,0);s.bars.add(b);}
            s.price=Math.max(0.01,s.price*Math.exp(shock(s,seconds)));
            var price=money(s.price);b.setClose(price);b.setHigh(b.getHigh().max(price));b.setLow(b.getLow().min(price));
            b.setVolume(b.getVolume()+Math.max(1,Math.round(s.volumePerSecond*seconds*(0.5+random.nextDouble()))));
            s.lastTick=now;s.quote=event(s,now);events.add(s.quote);
        }
        return events;
    }
    private TickEvent event(State s,LocalDateTime now){var b=s.bars.getLast();return TickEvent.builder().symbol(s.symbol).price(b.getClose()).volume(b.getVolume())
        .timestamp(now).sourceDate(now.toLocalDate()).feedMode("SIMULATED_LIVE").interval("1m").barTime(b.getBarTime())
        .open(b.getOpen()).high(b.getHigh()).low(b.getLow()).changePercent(BigDecimal.valueOf((s.price/s.reference-1)*100).setScale(4,RoundingMode.HALF_UP)).build();}
    public synchronized TickEvent quote(String symbol){var s=states.get(symbol);return s==null?null:s.quote;}
    public synchronized Snapshot snapshot(String symbol){
        var s=states.get(symbol);if(s==null)throw new IllegalArgumentException("Choose a simulated symbol");
        var copies=s.bars.stream().map(b->OhlcvBar.builder().interval("1m").barTime(b.getBarTime()).open(b.getOpen()).high(b.getHigh()).low(b.getLow()).close(b.getClose()).volume(b.getVolume()).build()).toList();
        return new Snapshot(s.quote,copies,startedAt);
    }
}
