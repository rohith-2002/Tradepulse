import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe,it,expect,vi } from 'vitest';
import { Dashboard } from './pages';
import { Api, Auth, Bar, DatasetInfo } from './core';
import { Feed } from './feed';

const march:Bar={barTime:'2020-03-31T00:00:00',open:1200,high:1250,low:1190,close:1234.5,volume:500};
function setup(history:Bar[]=[march]){
  const api={get:vi.fn((path:string)=>{
    if(path==='market/symbols')return of([{id:1,symbol:'RELIANCE',exchange:'NSE',sector:'Historical replay'}]);
    if(path.startsWith('market/ohlcv/'))return of(history);
    return throwError(()=>new Error('Unavailable'));
  })};
  TestBed.configureTestingModule({imports:[Dashboard],providers:[provideRouter([]),{provide:Api,useValue:api},{provide:Auth,useValue:{session:signal({userId:1})}},{provide:Feed,useValue:{status:signal('Disconnected'),watch:vi.fn(),stop:vi.fn()}}]});
  return TestBed.createComponent(Dashboard);
}
describe('Historical dashboard prices',()=>{
  it('shows the last candle price without waiting for metadata or replay',()=>{
    const f=setup();f.detectChanges();
    expect(f.nativeElement.querySelector('.price').textContent).toContain('1,234.50');
    expect(f.componentInstance.latest()?.sourceDate).toBe('2020-03-31');
    f.destroy();
  });
  it('reveals a continuation candle and removes old continuation candles on replay rollover',()=>{
    const f=setup();f.detectChanges();const c=f.componentInstance;
    c.dataset.set({enabled:true,to:'2020-03-31'} as DatasetInfo);
    c.applyQuote({price:1300,open:1240,high:1320,low:1230,volume:600,sourceDate:'2020-04-02',timestamp:'2026-10-18T10:00:00'});
    expect(c.bars().map(b=>b.barTime.slice(0,10))).toEqual(['2020-03-31','2020-04-02']);
    c.applyQuote({price:1250,open:1235,high:1260,low:1220,volume:550,sourceDate:'2020-04-01',timestamp:'2026-10-18T10:00:03'});
    expect(c.bars().map(b=>b.barTime.slice(0,10))).toEqual(['2020-03-31','2020-04-01']);
    f.detectChanges();expect(f.nativeElement.querySelector('.price').textContent).toContain('1,250.00');f.destroy();
  });
  it('shows an explicit unavailable marker instead of a blank price',()=>{
    const f=setup([]);f.detectChanges();expect(f.nativeElement.querySelector('.price').textContent).toContain('—');f.destroy();
  });
});
