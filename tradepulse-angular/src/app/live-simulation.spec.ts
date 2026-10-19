import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { afterEach, describe, it, expect, vi } from 'vitest';
import { Api, Bar } from './core';
import { Feed } from './feed';
import { LiveMarket } from './live-market';
import { Alerts, Backtests } from './pages';

const bar=(date:string,close=100):Bar=>({barTime:date,open:close,high:close+1,low:close-1,close,volume:100});
afterEach(()=>{TestBed.resetTestingModule();vi.useRealTimers();});
describe('Continuous simulation on alerts and strategy pages',()=>{
  it('updates the forming candle, ignores future bars and survives a failed poll',async()=>{
    vi.useFakeTimers();let date='2026-10-19T10:00:00',failed=false;
    const api={get:vi.fn((path:string)=>failed?throwError(()=>new Error('offline')):of(path.includes('ohlcv')?[bar('2026-10-19T09:59:00'),bar('2026-10-19T10:00:00'),bar('2026-10-19T10:01:00')]:{price:120,volume:100,barTime:date,sourceDate:'2026-10-19',timestamp:date,open:115,high:125,low:110}))};
    const feed={watch:vi.fn(),stop:vi.fn(),status:signal('Disconnected')};
    TestBed.configureTestingModule({imports:[LiveMarket],providers:[{provide:Api,useValue:api}]}).overrideComponent(LiveMarket,{set:{providers:[{provide:Feed,useValue:feed}]}});
    const f=TestBed.createComponent(LiveMarket);f.componentRef.setInput('symbol','RELIANCE');f.detectChanges();await vi.advanceTimersByTimeAsync(1);f.detectChanges();
    expect(f.nativeElement.querySelector('.price').textContent).toContain('120.00');
    expect(f.componentInstance.bars().at(-1)?.barTime).toContain('10:00:00');
    failed=true;await vi.advanceTimersByTimeAsync(3000);expect(f.componentInstance.quote()?.price).toBe(120);
    failed=false;date='2026-10-19T10:01:00';await vi.advanceTimersByTimeAsync(3000);
    expect(f.componentInstance.bars().map(b=>b.barTime)).toEqual(['2026-10-19T09:59:00','2026-10-19T10:00:00','2026-10-19T10:01:00']);
    f.destroy();const count=api.get.mock.calls.length;await vi.advanceTimersByTimeAsync(6000);expect(api.get).toHaveBeenCalledTimes(count);
  });
  it('refreshes triggered alerts without manual refresh and stops on navigation',async()=>{
    vi.useFakeTimers();let status='ACTIVE';const api={get:vi.fn((path:string)=>of(path==='market/symbols'?[]:[{id:1,status}]))};
    TestBed.configureTestingModule({providers:[{provide:Api,useValue:api}]});
    const c=TestBed.runInInjectionContext(()=>new Alerts());c.ngOnInit();status='TRIGGERED';await vi.advanceTimersByTimeAsync(3000);
    expect(c.alerts()[0].status).toBe('TRIGGERED');c.ngOnDestroy();const count=api.get.mock.calls.length;await vi.advanceTimersByTimeAsync(6000);expect(api.get).toHaveBeenCalledTimes(count);
  });
  it('refreshes a captured strategy without creating saved runs, retries failure and supports pause',async()=>{
    vi.useFakeTimers();let date='2020-04-01',fail=true;const api={post:vi.fn(()=>fail?throwError(()=>({error:{message:'Temporary failure'}})):of({asOf:date,quote:{price:100},result:{equityCurve:[{date,equity:100000}]}}))};
    TestBed.configureTestingModule({providers:[{provide:Api,useValue:api}]});
    const c=TestBed.runInInjectionContext(()=>new Backtests());c.symbol='RELIANCE';c.startReplay();await vi.advanceTimersByTimeAsync(1);expect(c.previewError()).toContain('Temporary');
    fail=false;c.symbol='TCS';await vi.advanceTimersByTimeAsync(3000);expect(c.preview()?.asOf).toBe(date);
    expect(api.post.mock.calls[1]).toEqual(['backtest/live',expect.objectContaining({parameters:expect.objectContaining({symbol:'RELIANCE'})})]);
    date='2020-04-02';await vi.advanceTimersByTimeAsync(3000);expect(c.preview()?.asOf).toBe(date);
    c.stopReplay();const count=api.post.mock.calls.length;await vi.advanceTimersByTimeAsync(6000);expect(api.post).toHaveBeenCalledTimes(count);c.ngOnDestroy();
  });
});
