package com.tradepulse.tradepulse.backtest.controller;
import com.tradepulse.tradepulse.backtest.entity.*;
import com.tradepulse.tradepulse.backtest.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDate;
import java.util.*;
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {
    private final BacktestService service;
    private final BacktestWorker worker;
    private final LiveStrategyService live;
    public record RunRequest(Long strategyId,LocalDate fromDate,LocalDate toDate){}
    public record StrategyRequest(String name,Map<String,Object> parameters){}
    @PostMapping("/live") public Map<String,Object> live(@RequestBody StrategyRequest r){return live.preview(r.name(),r.parameters());}
    @GetMapping("/strategies") public List<Strategy> strategies(@AuthenticationPrincipal UserDetails u){return service.getUserStrategies(u.getUsername());}
    @PostMapping("/strategies") public Strategy create(@AuthenticationPrincipal UserDetails u,@RequestBody StrategyRequest r){return service.create(u.getUsername(),r.name(),r.parameters());}
    @GetMapping("/runs") public List<BacktestRun> runs(@AuthenticationPrincipal UserDetails u){return service.getUserBacktestRuns(u.getUsername());}
    @GetMapping("/runs/{id}") public BacktestRun run(@AuthenticationPrincipal UserDetails u,@PathVariable Long id){return service.getRun(id,u.getUsername());}
    @PostMapping("/run") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public BacktestRun start(@AuthenticationPrincipal UserDetails u,@RequestBody RunRequest r){var run=service.initiateBacktest(u.getUsername(),r.strategyId(),r.fromDate(),r.toDate());try { worker.execute(run.getId()); } catch (org.springframework.core.task.TaskRejectedException ex) { /* Recovery scheduler will redispatch the persisted pending run. */ } return run;}
}
