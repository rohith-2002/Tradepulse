package com.tradepulse.tradepulse.market.controller;

import com.tradepulse.tradepulse.market.entity.MarketSymbol;
import com.tradepulse.tradepulse.market.entity.MarketTick;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import com.tradepulse.tradepulse.market.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Endpoints for symbols, ticks, and historic OHLCV data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/dataset")
    public java.util.Map<String,Object> dataset(){return marketDataService.datasetInfo();}
    @GetMapping("/quote/{symbol}")
    public com.tradepulse.kafka.event.TickEvent quote(@PathVariable String symbol){return marketDataService.quote(symbol);}

    @GetMapping("/symbols")
    @Operation(summary = "Get all active market symbols")
    public List<MarketSymbol> getSymbols() {
        return marketDataService.getAllActiveSymbols();
    }

    @GetMapping("/ticks/{symbol}")
    @Operation(summary = "Get recent ticks for a symbol")
    public List<MarketTick> getRecentTicks(@PathVariable String symbol) {
        return marketDataService.getRecentTicks(symbol);
    }

    @GetMapping("/ohlcv/{symbol}")
    @Operation(summary = "Get OHLCV bars for a symbol and interval")
    public List<OhlcvBar> getOhlcvData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval) {
        return marketDataService.getHistoricalBars(symbol, interval);
    }
}
