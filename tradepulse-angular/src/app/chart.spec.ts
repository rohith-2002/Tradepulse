import { TestBed } from '@angular/core/testing';
import { Chart } from './chart';
import { Candles } from './candles';
import { describe,it,expect } from 'vitest';
describe('Market charts',()=>{
  it('shows an empty state instead of an invalid chart',()=>{const f=TestBed.createComponent(Chart);f.detectChanges();expect(f.nativeElement.textContent).toContain('No chart data');});
  it('renders flat price histories without NaN or infinity',()=>{const f=TestBed.createComponent(Chart);f.componentRef.setInput('values',[100,100,100]);f.detectChanges();const points=f.nativeElement.querySelector('polyline').getAttribute('points');expect(points).not.toMatch(/NaN|Infinity/);});
  it('provides an OHLC tooltip and correct rising candle color',()=>{const f=TestBed.createComponent(Candles);f.componentRef.setInput('bars',[{barTime:'2026-01-01',open:100,high:120,low:90,close:110,volume:10}]);f.detectChanges();expect(f.nativeElement.querySelector('title').textContent).toContain('Open 100');expect(f.nativeElement.querySelector('g').getAttribute('fill')).toBe('#48d5a7');});
});
