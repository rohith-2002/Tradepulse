import { Component, Input, Output, EventEmitter, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, switchMap, forkJoin, catchError, of } from 'rxjs';
import { Api, Bar, Tick } from './core';
import { Feed } from './feed';
import { Candles } from './candles';

@Component({selector:'tp-live-market',standalone:true,imports:[CommonModule,Candles],providers:[Feed],template:`
@if(symbol){<section class="panel"><div class="section-title"><div><span class="eyebrow">{{symbol}} · SIMULATED MARKET</span><h2 class="price">{{quote() ? (quote()!.price|number:'1.2-2') : '—'}} <small>INR</small></h2></div><span class="badge">{{feed.status()}}</span></div>
<p class="muted">Updated: {{quote()?.timestamp|date:'mediumTime'}} · Current minute volume: {{quote()?.volume|number}} · Change: {{quote()?.changePercent|number:'1.2-2'}}%</p>
<tp-candles [bars]="bars()"/>
<p class="muted">Continuous simulated ticks · 1-minute candles · Initial candles are generated warm-up data calibrated from historical volatility. No historical replay or exchange connection.</p>
<p class="muted" role="status">{{warning()}}</p></section>}`})
export class LiveMarket implements OnChanges,OnDestroy {
  @Input() symbol=''; @Output() priceChange=new EventEmitter<Tick>();
  api=inject(Api);feed=inject(Feed);quote=signal<Tick|null>(null);bars=signal<Bar[]>([]);warning=signal('');private sub?:Subscription;
  ngOnChanges(){
    this.sub?.unsubscribe();this.feed.stop();this.quote.set(null);this.bars.set([]);this.warning.set('');
    if(!this.symbol)return;
    this.feed.watch(this.symbol,t=>this.accept(t));
    const symbol=encodeURIComponent(this.symbol);
    this.sub=timer(0,1000).pipe(switchMap(()=>forkJoin({
      bars:this.api.get<Bar[]>('market/ohlcv/'+symbol+'?interval=1m').pipe(catchError(()=>of(null))),
      quote:this.api.get<Tick>('market/quote/'+symbol).pipe(catchError(()=>of(null)))
    }))).subscribe(({bars,quote})=>{
      if(quote)this.accept(quote);
      else if(!this.quote()&&bars?.length){const b=bars.at(-1)!;this.accept({price:b.close,volume:b.volume,sourceDate:b.barTime.slice(0,10)});}
      if(bars){const time=this.quote()?.barTime;this.bars.set(bars.filter(b=>!time||b.barTime<=time));if(this.quote())this.merge(this.quote()!);}
      this.warning.set(!quote?'Simulation connection unavailable. Showing the last known price.':!bars?'History unavailable; price updates continue.':'');
    });
  }
  accept(t:Tick){
    if(!Number.isFinite(t.price)||t.price<=0)return;
    const previous=this.quote();if(t.timestamp&&previous?.timestamp&&t.timestamp<previous.timestamp)return;
    this.quote.set(t);this.merge(t);this.priceChange.emit(t);
  }
  private merge(t:Tick){
    if(!t.barTime)return;
    let bars=this.bars().filter(b=>b.barTime<=t.barTime!);
    if(t.open!==undefined&&t.high!==undefined&&t.low!==undefined){bars=bars.filter(b=>b.barTime!==t.barTime);bars.push({barTime:t.barTime,open:t.open,high:t.high,low:t.low,close:t.price,volume:t.volume});}
    this.bars.set(bars);
  }
  ngOnDestroy(){this.sub?.unsubscribe();this.feed.stop();}
}
