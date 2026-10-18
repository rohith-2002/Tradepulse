import { Component, input, computed } from '@angular/core';
@Component({selector:'tp-chart',standalone:true,template:`
  @if(values().length>1){<svg viewBox="0 0 800 240" role="img" [attr.aria-label]="label()" preserveAspectRatio="none"><defs><linearGradient id="fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#48d5a7" stop-opacity=".25"/><stop offset="100%" stop-color="#48d5a7" stop-opacity="0"/></linearGradient></defs><path d="M0 60H800 M0 120H800 M0 180H800" stroke="#253545" fill="none"/><polygon [attr.points]="'0,240 '+points()+' 800,240'" fill="url(#fill)"/><polyline [attr.points]="points()" fill="none" stroke="#48d5a7" stroke-width="2.5" vector-effect="non-scaling-stroke"/></svg>}@else{<div class="empty">No chart data yet. Select a symbol or run a backtest.</div>}`,
  styles:[`svg{width:100%;height:240px;display:block}.empty{padding:80px 20px;text-align:center;color:#90a4b8}`]})
export class Chart {
  values=input<number[]>([]);label=input('Price history');
  points=computed(()=>{const v=this.values(),min=Math.min(...v),max=Math.max(...v),range=max-min||1;return v.map((x,i)=>`${i*800/(v.length-1)},${220-(x-min)*200/range}`).join(' ');});
}
