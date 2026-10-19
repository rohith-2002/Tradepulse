package com.tradepulse.tradepulse.market.service;

import com.tradepulse.tradepulse.market.entity.*;
import com.tradepulse.tradepulse.market.repository.*;
import com.tradepulse.kafka.event.TickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketDataService {
    private final MarketSymbolRepository symbols;
    private final MarketTickRepository ticks;
    private final OhlcvBarRepository bars;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final HistoricalDataset dataset;
    private final org.springframework.beans.factory.ObjectProvider<DemoMarketFeed> replayFeed;
    @org.springframework.beans.factory.annotation.Value("${tradepulse.demo.enabled:false}")
    private boolean demo;
    public Map<String,Object> datasetInfo(){
        if(!demo)return Map.of("enabled",false);
        var s=dataset.snapshot();
        return Map.of("enabled",true,"mode","SIMULATED_LIVE","source",s.source(),"sourceUrl",s.sourceUrl(),"from",s.from(),"to",s.to(),"notes","Continuous synthetic ticks calibrated from historical daily returns; 1-minute candles; not exchange quotes. Session resets on server restart.","instruments",s.instruments().stream().map(i->Map.of("symbol",i.symbol(),"dailyVolatilityPercent",i.dailyVolatilityPercent())).toList());
    }
    public TickEvent quote(String symbol){
        var feed=replayFeed.getIfAvailable();
        if(demo&&dataset.contains(symbol)&&feed!=null&&feed.quote(symbol)!=null)return feed.quote(symbol);
        var recent=getRecentTicks(symbol);
        if(!recent.isEmpty()){
            var t=recent.getFirst();return TickEvent.builder().symbol(symbol).price(t.getPrice()).volume(t.getVolume()).timestamp(t.getTs()).sourceDate(t.getSourceDate()).feedMode(t.getFeedMode()).changePercent(t.getChangePercent()).build();
        }
        var history=getHistoricalBars(symbol,"1d");
        if(history.isEmpty())throw new NoSuchElementException("No recorded price available for "+symbol);
        var b=history.getLast();return TickEvent.builder().symbol(symbol).price(b.getClose()).volume(b.getVolume()).sourceDate(b.getBarTime().toLocalDate()).open(b.getOpen()).high(b.getHigh()).low(b.getLow()).feedMode("HISTORICAL_CLOSE").build();
    }
    public List<MarketSymbol> getAllActiveSymbols(){return symbols.findAll().stream().filter(s->Boolean.TRUE.equals(s.getActive())).toList();}
    public List<MarketTick> getRecentTicks(String symbol){return ticks.findTop100BySymbolSymbolOrderByTsDesc(symbol).stream().filter(t->!demo||!dataset.contains(symbol)||"SIMULATED_LIVE".equals(t.getFeedMode())).toList();}
    public List<OhlcvBar> getHistoricalBars(String symbol,String interval){
        if(interval.equals("1m")){
            var feed=replayFeed.getIfAvailable();
            if(feed==null)throw new IllegalArgumentException("Live simulation is not enabled");
            var history=feed.snapshot(symbol).bars();return history.subList(Math.max(0,history.size()-120),history.size());
        }
        if(!Set.of("1d","1h").contains(interval))throw new IllegalArgumentException("Use 1m, 1d or 1h interval");
        String key="market:ohlcv:"+symbol+":"+interval+(demo?":"+dataset.snapshot().id():"");
        boolean replay=demo&&dataset.contains(symbol);
        if(!replay)try {String value=redis.opsForValue().get(key);if(value!=null)return json.readValue(value,new TypeReference<List<OhlcvBar>>(){});}catch(Exception ignored){}
        LocalDate visibleTo=dataset.snapshot().to();
        var result=bars.findBySymbolSymbolAndIntervalAndBarTimeBetweenOrderByBarTimeAsc(symbol,interval,replay?dataset.snapshot().from().atStartOfDay():LocalDate.now().minusYears(1).atStartOfDay(),replay?visibleTo.atTime(23,59,59):LocalDateTime.now());
        if(!replay)try{redis.opsForValue().set(key,json.writeValueAsString(result),Duration.ofSeconds(60));}catch(Exception ignored){}
        return result;
    }
    @Transactional
    public void ingest(TickEvent event){
        if(event.getSymbol()==null||event.getTimestamp()==null||event.getPrice()==null||event.getPrice().signum()<=0||event.getVolume()==null||event.getVolume()<0)throw new IllegalArgumentException("Invalid market tick");
        var symbol=symbols.findBySymbol(event.getSymbol()).orElseThrow();
        if(!ticks.existsBySymbolSymbolAndTs(event.getSymbol(),event.getTimestamp()))ticks.save(MarketTick.builder().symbol(symbol).price(event.getPrice()).volume(event.getVolume()).ts(event.getTimestamp()).sourceDate(event.getSourceDate()).feedMode(event.getFeedMode()).changePercent(event.getChangePercent()).build());
        try{redis.opsForValue().set("market:price:"+event.getSymbol(),json.writeValueAsString(event),Duration.ofSeconds(30));}catch(Exception ignored){}
    }
}
