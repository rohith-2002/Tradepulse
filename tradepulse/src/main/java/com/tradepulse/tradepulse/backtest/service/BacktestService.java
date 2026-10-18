package com.tradepulse.tradepulse.backtest.service;

import com.tradepulse.tradepulse.auth.repository.UserRepository;
import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import com.tradepulse.tradepulse.backtest.entity.*;
import com.tradepulse.tradepulse.backtest.repository.*;
import com.tradepulse.tradepulse.market.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class BacktestService {
    private final StrategyRepository strategies;
    private final BacktestRunRepository runs;
    private final UserRepository users;
    private final MarketSymbolRepository symbols;
    private final BacktestEngine engine;
    public List<Strategy> getUserStrategies(String email){return strategies.findByUserId(users.findByEmail(email).orElseThrow().getId());}
    public List<BacktestRun> getUserBacktestRuns(String email){return runs.findByStrategyUserId(users.findByEmail(email).orElseThrow().getId());}
    public BacktestRun getRun(Long id,String email){var run=runs.findById(id).orElseThrow(); own(run.getStrategy(),email);return run;}
    private void own(Strategy s,String email){if(!s.getUser().getEmail().equals(email))throw new NoSuchElementException();}
    @Transactional
    public Strategy create(String email,String name,Map<String,Object> parameters){
        var strategy=Strategy.builder().name(name).parameters(parameters).user(users.findByEmail(email).orElseThrow()).build();
        engine.validate(strategy);
        symbols.findBySymbol(Objects.toString(parameters.get("symbol"),"")).orElseThrow(()->new IllegalArgumentException("Choose a valid symbol"));
        if(!Set.of("1d","1h").contains(parameters.getOrDefault("interval","1d")))throw new IllegalArgumentException("Use 1d or 1h interval");
        return strategies.save(strategy);
    }
    @Transactional
    public BacktestRun initiateBacktest(String email,Long strategyId,LocalDate from,LocalDate to){
        if(from==null||to==null||from.isAfter(to)||to.isAfter(LocalDate.now())||from.plusYears(5).isBefore(to))throw new IllegalArgumentException("Choose a past date range of at most five years");
        var s=strategies.findById(strategyId).orElseThrow();own(s,email);
        return runs.save(BacktestRun.builder().strategy(s).fromDate(from).toDate(to).status(BacktestRun.BacktestStatus.PENDING).build());
    }
}
