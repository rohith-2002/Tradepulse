import { Component, input, computed } from '@angular/core';
import { Bar } from './core';
@Component({selector:'tp-candles',standalone:true,template:`
@if(candles().length){<svg viewBox="0 0 800 240" role="img" aria-label="Daily OHLC candlestick chart"><path d="M0 60H800 M0 120H800 M0 180H800" stroke="#253545" fill="none"/>@for(c of candles();track c.time){<g [attr.stroke]="c.color" [attr.fill]="c.color"><title>{{c.time}} · Open {{c.open}} · High {{c.high}} · Low {{c.low}} · Close {{c.close}}</title><line [attr.x1]="c.x" [attr.x2]="c.x" [attr.y1]="c.top" [attr.y2]="c.bottom"/><rect [attr.x]="c.x-4" [attr.y]="c.y" width="8" [attr.height]="c.height"/></g>}</svg>}@else{<p class="empty">No OHLC history available.</p>}`,
styles:[`svg{width:100%;height:240px;display:block}`]})
export class Candles {
  bars=input<Bar[]>([]);
  candles=computed(()=>{const b=this.bars().slice(-60),min=Math.min(...b.map(v=>v.low)),max=Math.max(...b.map(v=>v.high)),scale=(v:number)=>220-(v-min)*200/(max-min||1);return b.map((v,i)=>({...v,time:v.barTime,x:10+i*780/Math.max(1,b.length-1),top:scale(v.high),bottom:scale(v.low),y:scale(Math.max(v.open,v.close)),height:Math.max(1,Math.abs(scale(v.open)-scale(v.close))),color:v.close>=v.open?'#48d5a7':'#ff8d8d'}));});
}
