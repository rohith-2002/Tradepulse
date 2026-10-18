package com.tradepulse.tradepulse.backtest.service;

import com.tradepulse.tradepulse.backtest.entity.BacktestRun.BacktestStatus;
import com.tradepulse.tradepulse.backtest.repository.BacktestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="tradepulse.recovery.enabled",havingValue="true",matchIfMissing=true)
public class BacktestRecovery {
    private final BacktestRunRepository runs;
    private final BacktestWorker worker;
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void recover(){
        var pending=new java.util.ArrayList<>(runs.findTop20ByStatusOrderByIdAsc(BacktestStatus.PENDING));
        pending.addAll(runs.findTop20ByStatusAndStartedAtBeforeOrderByIdAsc(BacktestStatus.RUNNING,java.time.LocalDateTime.now().minusMinutes(15)));
        for(var run:pending){try{worker.execute(run.getId());}catch(org.springframework.core.task.TaskRejectedException ex){break;}}
    }
}
