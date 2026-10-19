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
    private volatile SimulatedMarket simulation;
    public TickEvent quote(String symbol){return simulation==null?null:simulation.quote(symbol);}
    public SimulatedMarket.Snapshot snapshot(String symbol){if(simulation==null)throw new IllegalArgumentException("Simulation is starting");return simulation.snapshot(symbol);}
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
        simulation=new SimulatedMarket(dataset,Clock.system(ZoneId.of("Asia/Kolkata")),new Random());
        ready=true;
    }
    @Scheduled(fixedDelay=1000,initialDelay=10000)
    public void tick(){
        if(!ready)return;
        // Update every symbol before publishing so a slow broker cannot leave other quotes unchanged.
        for(var event:simulation.advance())producer.sendTick(event);
    }
}
