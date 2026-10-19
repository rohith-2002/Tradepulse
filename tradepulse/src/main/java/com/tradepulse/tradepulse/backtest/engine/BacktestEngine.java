package com.tradepulse.tradepulse.backtest.engine;

import com.tradepulse.tradepulse.backtest.entity.Strategy;
import com.tradepulse.tradepulse.market.entity.OhlcvBar;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class BacktestEngine {
    public void validate(Strategy strategy) {
        var p = strategy.getParameters();
        if (p == null || !Set.of("SMA", "RSI", "MACD").contains(strategy.getName())) throw new IllegalArgumentException("Choose SMA, RSI or MACD");
        for (String key : List.of("fastPeriod", "slowPeriod", "period", "signalPeriod")) {
            double n = number(p, key, key.equals("slowPeriod") ? 26 : 14);
            if (n < 2 || n > 250 || n != Math.floor(n)) throw new IllegalArgumentException("Periods must be whole numbers from 2 to 250");
        }
        if (number(p,"fastPeriod",9) >= number(p,"slowPeriod",26)) throw new IllegalArgumentException("Fast period must be less than slow period");
        if (number(p,"oversold",30) < 0 || number(p,"overbought",70) > 100 || number(p,"oversold",30) >= number(p,"overbought",70)) throw new IllegalArgumentException("Invalid RSI thresholds");
        if (number(p,"initialCapital",100000) <= 0 || number(p,"initialCapital",100000) > 1e9 || number(p,"feeBps",10) < 0 || number(p,"feeBps",10) > 100) throw new IllegalArgumentException("Invalid capital or fee");
    }
    private double number(Map<String,Object> p, String key, double fallback) {
        Object v = p.getOrDefault(key, fallback);
        if (!(v instanceof Number) || !Double.isFinite(((Number)v).doubleValue())) throw new IllegalArgumentException("Invalid parameter: " + key);
        return ((Number)v).doubleValue();
    }
    public Map<String,Object> execute(Strategy strategy, List<OhlcvBar> history) {
        return execute(strategy,history,null);
    }
    public Map<String,Object> execute(Strategy strategy, List<OhlcvBar> history, java.time.LocalDate tradingFrom) {
        return execute(strategy, history, tradingFrom, true);
    }
    public Map<String,Object> execute(Strategy strategy, List<OhlcvBar> history, java.time.LocalDate tradingFrom, boolean liquidate) {
        return calculate(strategy,history,tradingFrom,liquidate,-1);
    }
    public Map<String,Object> executeLive(Strategy strategy,List<OhlcvBar> history,int firstTradingBar){
        return calculate(strategy,history,null,false,firstTradingBar);
    }
    private Map<String,Object> calculate(Strategy strategy,List<OhlcvBar> history,java.time.LocalDate tradingFrom,boolean liquidate,int firstTradingBar){
        validate(strategy);
        var p = strategy.getParameters();
        int fast=(int)number(p,"fastPeriod",9), slow=(int)number(p,"slowPeriod",26), period=(int)number(p,"period",14), signal=(int)number(p,"signalPeriod",9);
        int warmup = strategy.getName().equals("RSI") ? period + 1 : slow + signal;
        if (history == null || history.size() < warmup + 2) throw new IllegalArgumentException("Insufficient history: need at least " + (warmup+2) + " bars");
        double[] close=history.stream().mapToDouble(b->b.getClose().doubleValue()).toArray();
        for (int i=0;i<history.size();i++) {
            if (close[i]<=0 || history.get(i).getOpen().doubleValue()<=0) throw new IllegalArgumentException("Prices must be positive");
            if (i>0 && !history.get(i).getBarTime().isAfter(history.get(i-1).getBarTime())) throw new IllegalArgumentException("History must be chronological and unique");
        }
        double[] a,b;
        if (strategy.getName().equals("SMA")) { a=sma(close,fast); b=sma(close,slow); }
        else if (strategy.getName().equals("MACD")) {
            a=ema(close,fast); double[] s=ema(close,slow);
            for(int i=0;i<a.length;i++) a[i]-=s[i];
            b=ema(a,signal);
        } else { a=rsi(close,period); b=new double[close.length]; }
        double initial=number(p,"initialCapital",100000), cash=initial, quantity=0, entryCost=0, peak=initial, drawdown=0, fee=number(p,"feeBps",10)/10000;
        int wins=0; List<Map<String,Object>> curve=new ArrayList<>(), trades=new ArrayList<>();
        int start=warmup;
        if(tradingFrom!=null){
            start=0;
            while(start<history.size() && history.get(start).getBarTime().toLocalDate().isBefore(tradingFrom))start++;
            if(start<warmup)throw new IllegalArgumentException("Not enough earlier bars to warm up these periods; choose smaller periods or a later start date");
            if(start>=history.size())throw new IllegalArgumentException("No candles in the requested trading period");
        }
        if(firstTradingBar>=0){
            if(firstTradingBar<warmup||firstTradingBar>=history.size())throw new IllegalArgumentException("Periods exceed the available simulation warm-up; choose smaller periods");
            start=firstTradingBar;
        }
        for(int i=start;i<close.length;i++) {
            // A signal from yesterday's close is executed at today's open: no same-bar lookahead.
            int t=i-1;
            boolean buy, sell;
            if(strategy.getName().equals("RSI")) { buy=a[t]<number(p,"oversold",30); sell=a[t]>number(p,"overbought",70); }
            else { buy=a[t]>b[t] && a[t-1]<=b[t-1]; sell=a[t]<b[t] && a[t-1]>=b[t-1]; }
            double price=history.get(i).getOpen().doubleValue();
            if(buy && quantity==0 && (!liquidate || i<close.length-1)) { entryCost=cash; quantity=cash/(price*(1+fee)); cash=0; }
            else if(sell && quantity>0) {
                cash=quantity*price*(1-fee); double profit=cash-entryCost; if(profit>0)wins++;
                trades.add(Map.of("date",history.get(i).getBarTime().toString(),"exitPrice",round(price),"profit",round(profit))); quantity=0;
            }
            if(liquidate && i==close.length-1 && quantity>0) {
                cash=quantity*close[i]*(1-fee); double profit=cash-entryCost; if(profit>0)wins++;
                trades.add(Map.of("date",history.get(i).getBarTime().toString(),"exitPrice",round(close[i]),"profit",round(profit))); quantity=0;
            }
            double equity=cash+quantity*close[i]; peak=Math.max(peak,equity); drawdown=Math.max(drawdown,(peak-equity)/peak);
            curve.add(Map.of("date",history.get(i).getBarTime().toString(),"equity",round(equity)));
        }
        Map<String,Object> result=new LinkedHashMap<>();
        double balance=cash+quantity*close[close.length-1];
        result.put("strategyType",strategy.getName()); result.put("initialCapital",initial); result.put("finalBalance",round(balance));
        result.put("netProfit",round(balance-initial)); result.put("returnPercent",round((balance/initial-1)*100));
        result.put("position",quantity>0?"LONG":"CASH"); result.put("quantity",quantity); result.put("cash",round(cash));
        result.put("maxDrawdownPercent",round(drawdown*100)); result.put("totalTrades",trades.size());
        result.put("winRate",trades.isEmpty()?0:(double)wins/trades.size()); result.put("equityCurve",curve); result.put("trades",trades);
        result.put("assumptions","Long-only fractional positions; next-bar open fills; fees on executed trades; "+(liquidate?"final close liquidation":"open positions valued at latest revealed close")+"; no slippage or taxes.");
        return result;
    }
    private double round(double value){ return Math.round(value*100)/100.0; }
    private double[] sma(double[] values,int n){double[] out=new double[values.length];double sum=0;for(int i=0;i<values.length;i++){sum+=values[i];if(i>=n)sum-=values[i-n];out[i]=i>=n-1?sum/n:Double.NaN;}return out;}
    private double[] ema(double[] values,int n){double[] out=new double[values.length];out[0]=values[0];for(int i=1;i<values.length;i++)out[i]=out[i-1]+2.0/(n+1)*(values[i]-out[i-1]);return out;}
    private double[] rsi(double[] values,int n){
        double[] out=new double[values.length];Arrays.fill(out,Double.NaN);double gain=0,loss=0;
        for(int i=1;i<values.length;i++){double d=values[i]-values[i-1];if(i<=n){gain+=Math.max(d,0)/n;loss+=Math.max(-d,0)/n;}else{gain=(gain*(n-1)+Math.max(d,0))/n;loss=(loss*(n-1)+Math.max(-d,0))/n;}if(i>=n)out[i]=loss==0?(gain==0?50:100):100-100/(1+gain/loss);}return out;
    }
}
