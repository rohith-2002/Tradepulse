package com.tradepulse.tradepulse.backtest.service;

import com.tradepulse.tradepulse.backtest.repository.BacktestRunRepository;
import com.tradepulse.tradepulse.backtest.entity.BacktestRun;
import com.tradepulse.tradepulse.backtest.engine.BacktestEngine;
import com.tradepulse.tradepulse.market.repository.OhlcvBarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BacktestWorker {
    private final BacktestRunRepository runs;
    private final OhlcvBarRepository bars;
    private final BacktestEngine engine;
    private final TransactionTemplate tx;
    private final com.tradepulse.tradepulse.events.EventOutbox outbox;
    @Async
    public void execute(Long id){
        var run=tx.execute(s->{var r=runs.findLockedById(id).orElseThrow();
            if(r.getStatus()!=BacktestRun.BacktestStatus.PENDING && !(r.getStatus()==BacktestRun.BacktestStatus.RUNNING && r.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(15))))return null;
            r.setStatus(BacktestRun.BacktestStatus.RUNNING);r.setStartedAt(LocalDateTime.now());return runs.save(r);});
        if(run==null)return;
        try{
            var p=run.getStrategy().getParameters();
            var history=bars.findBySymbolSymbolAndIntervalAndBarTimeBetweenOrderByBarTimeAsc(p.get("symbol").toString(),p.getOrDefault("interval","1d").toString(),run.getFromDate().minusYears(3).atStartOfDay(),run.getToDate().plusDays(1).atStartOfDay().minusNanos(1));
            run.setResult(engine.execute(run.getStrategy(),history,run.getFromDate()));run.setStatus(BacktestRun.BacktestStatus.COMPLETED);
        }catch(Exception ex){run.setResult(Map.of("error",ex instanceof IllegalArgumentException?ex.getMessage():"Backtest failed; check server logs"));run.setStatus(BacktestRun.BacktestStatus.FAILED);}
        run.setFinishedAt(LocalDateTime.now());tx.executeWithoutResult(s->{
            runs.save(run);
            outbox.enqueue(com.tradepulse.kafka.KafkaTopics.BACKTEST_RESULT, run.getId().toString(), com.tradepulse.kafka.event.BacktestResultEvent.builder()
                .runId(run.getId()).strategyId(run.getStrategy().getId()).userId(run.getStrategy().getUser().getId()).status(run.getStatus().name()).metrics(run.getResult()).finishedAt(run.getFinishedAt()).build());
        });
    }
}
