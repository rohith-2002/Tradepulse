package com.tradepulse.tradepulse.market.service;

import com.tradepulse.tradepulse.market.entity.*;
import com.tradepulse.tradepulse.market.repository.*;
import com.tradepulse.kafka.producer.MarketTickProducer;
import com.tradepulse.kafka.event.TickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="tradepulse.demo.enabled",havingValue="true")
public class DemoMarketFeed implements ApplicationRunner {
    private final MarketSymbolRepository symbols;
    private final OhlcvBarRepository bars;
    private final MarketTickProducer producer;
    private final HistoricalDataset dataset;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private volatile boolean ready;
    private int cursor;
    private final Map<String,TickEvent> latest=new java.util.concurrent.ConcurrentHashMap<>();
    public TickEvent quote(String symbol){return latest.get(symbol);}
    public void run(ApplicationArguments args){
        tx.executeWithoutResult(status->{
        for(var instrument:dataset.snapshot().instruments()){
            String name=instrument.symbol();var symbol=symbols.findBySymbol(name).orElseGet(()->symbols.save(MarketSymbol.builder().symbol(name).exchange("NSE").sector("Historical replay").active(true).build()));
            var existing=bars.findBySymbolSymbolAndIntervalOrderByBarTimeAsc(name,"1d");
            // Only the old demo seeder's history is removed; user records remain intact.
            if("Demo".equals(symbol.getSector())){bars.deleteAllInBatch(existing);existing=List.of();symbol.setSector("Historical replay");symbols.save(symbol);}
            Map<LocalDate,OhlcvBar> byDate=new HashMap<>();existing.forEach(b->byDate.put(b.getBarTime().toLocalDate(),b));
            for(var c:instrument.bars()){
                var bar=byDate.getOrDefault(c.date(),OhlcvBar.builder().symbol(symbol).interval("1d").barTime(c.date().atStartOfDay()).build());
                bar.setOpen(c.open());bar.setHigh(c.high());bar.setLow(c.low());bar.setClose(c.close());bar.setVolume(c.volume());bars.save(bar);
            }
        }
        });
        for(var i:dataset.snapshot().instruments()){try{redis.delete("market:ohlcv:"+i.symbol()+":1d");redis.delete("market:price:"+i.symbol());}catch(Exception ignored){}}
        for(var instrument:dataset.snapshot().instruments()){
            var c=dataset.month(instrument).getLast();
            latest.put(instrument.symbol(),event(instrument,c));
        }
        ready=true;
    }
    @Scheduled(fixedDelay=3000,initialDelay=10000)
    public void tick(){
        if(!ready)return;
        for(var instrument:dataset.snapshot().instruments()){
            var continuation=dataset.continuation(instrument);var candle=continuation.get(cursor%continuation.size());
            var event=event(instrument,candle);latest.put(instrument.symbol(),event);
            producer.sendTick(event);
        }
        cursor=(cursor+1)%dataset.continuation(dataset.snapshot().instruments().getFirst()).size();
    }
    private TickEvent event(HistoricalDataset.Instrument instrument,HistoricalDataset.Candle candle){
        var previous=instrument.bars().stream().filter(b->b.date().isBefore(candle.date())).reduce((a,b)->b).orElseThrow();
        var change=candle.close().subtract(previous.close()).multiply(BigDecimal.valueOf(100)).divide(previous.close(),4,java.math.RoundingMode.HALF_UP);
        return TickEvent.builder().symbol(instrument.symbol()).price(candle.close()).open(candle.open()).high(candle.high()).low(candle.low()).volume(candle.volume()).changePercent(change)
            .timestamp(LocalDateTime.now()).sourceDate(candle.date()).feedMode("HISTORICAL_REPLAY").build();
    }
}
