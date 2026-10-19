package com.tradepulse.tradepulse.backtest.service;

import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import com.tradepulse.tradepulse.backtest.entity.Strategy;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import com.tradepulse.tradepulse.market.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only strategy evaluation over an atomic snapshot of generated minute candles. */
@Service
@RequiredArgsConstructor
public class LiveStrategyService {
    private final HistoricalDataset dataset;
    private final ObjectProvider<DemoMarketFeed> feeds;
    private final BacktestEngine engine;

    public Map<String,Object> preview(String name, Map<String,Object> parameters) {
        var strategy=Strategy.builder().name(name).parameters(parameters).build();
        engine.validate(strategy);
        String symbol=Objects.toString(parameters.get("symbol"),"");
        if(!dataset.contains(symbol))throw new IllegalArgumentException("Choose a simulation symbol");
        var feed=feeds.getIfAvailable();
        if(feed==null)throw new IllegalArgumentException("Live simulation is not running; enable the demo profile");
        var snapshot=feed.snapshot(symbol);
        return Map.of("symbol",symbol,"asOf",snapshot.quote().getTimestamp(),"quote",snapshot.quote(),"from",snapshot.startedAt(),
            "result",engine.executeLive(strategy,snapshot.bars(),SimulatedMarket.WARMUP_BARS));
    }
}
