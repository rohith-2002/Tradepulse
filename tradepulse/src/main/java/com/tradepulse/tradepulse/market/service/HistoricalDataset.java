package com.tradepulse.tradepulse.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Offline source snapshot: invalid data fails startup, never generates substitutes. */
@Component
public class HistoricalDataset {
    public record Candle(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
    public record Instrument(String symbol, String sourceUrl, String sha256, double dailyVolatilityPercent, List<Candle> bars) {}
    public record Snapshot(String id, String source, String sourceUrl, String revision, String downloadedAt,
                           String exchange, String currency, String interval, LocalDate from, LocalDate to,
                           LocalDate warmupFrom, LocalDate replayFrom, LocalDate replayTo, String notes, List<Instrument> instruments) {}
    private final Snapshot snapshot;
    public HistoricalDataset(ObjectMapper mapper) throws IOException {
        try(var stream = new ClassPathResource("market/history.json").getInputStream()) {
            snapshot = mapper.readValue(stream, Snapshot.class);
        }
        for (var instrument : snapshot.instruments()) {
            LocalDate previous = null;
            for (var b : instrument.bars()) {
                if (b.open().signum()<=0 || b.low().signum()<=0 || b.close().signum()<=0 ||
                    b.high().compareTo(b.open().max(b.close()))<0 || b.low().compareTo(b.open().min(b.close()))>0 ||
                    b.volume()<0 || (previous!=null && !b.date().isAfter(previous)))
                    throw new IllegalStateException("Invalid historical candle: " + instrument.symbol());
                previous=b.date();
            }
        }
    }
    public Snapshot snapshot(){return snapshot;}
    public boolean contains(String symbol){return snapshot.instruments().stream().anyMatch(i->i.symbol().equals(symbol));}
    public List<Candle> month(Instrument instrument){return instrument.bars().stream().filter(b->!b.date().isBefore(snapshot.from()) && !b.date().isAfter(snapshot.to())).toList();}
    public List<Candle> continuation(Instrument instrument){return instrument.bars().stream().filter(b->!b.date().isBefore(snapshot.replayFrom()) && !b.date().isAfter(snapshot.replayTo())).toList();}
}
