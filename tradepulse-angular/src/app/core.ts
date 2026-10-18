import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Router, CanActivateFn } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, tap, throwError } from 'rxjs';

export interface Session { accessToken:string; refreshToken:string; email:string; userId:number; }
export interface SymbolInfo { id:number; symbol:string; exchange:string; sector:string; }
export interface Tick { price:number; volume:number; timestamp?:string; ts?:string; changePercent?:number; sourceDate?:string; feedMode?:string; open?:number; high?:number; low?:number; }
export interface Bar { open:number; high:number; low:number; close:number; volume:number; barTime:string; }
export interface Alert { id:number; symbol:SymbolInfo; conditionType:string; threshold:number; status:string; }
export interface Notice { id:number; message:string; status:string; sentAt:string; }
export interface Strategy { id:number; name:string; parameters:Record<string,unknown>; }
export interface Result { finalBalance:number; netProfit:number; returnPercent:number; maxDrawdownPercent:number; totalTrades:number; winRate:number; equityCurve:{date:string;equity:number}[]; assumptions:string; error?:string; }
export interface Run { id:number; strategy:Strategy; status:string; fromDate:string; toDate:string; result?:Result; }
@Injectable({providedIn:'root'})
export class Auth {
  private http=inject(HttpClient); private router=inject(Router);
  session=signal<Session|null>(this.restore()); private refreshing?:Observable<Session>;
  private restore():Session|null {try{return JSON.parse(sessionStorage.getItem('tp-session')||'null');}catch{return null;}}
  private save(s:Session){sessionStorage.setItem('tp-session',JSON.stringify(s));this.session.set(s);}
  login(email:string,password:string,register=false){return this.http.post<Session>('/api/auth/'+(register?'register':'login'),{email,password}).pipe(tap(s=>this.save(s)));}
  refresh(){
    if(!this.refreshing)this.refreshing=this.http.post<Session>('/api/auth/refresh',{refreshToken:this.session()?.refreshToken}).pipe(tap(s=>this.save(s)),catchError(e=>{this.clear();return throwError(()=>e);}),finalize(()=>this.refreshing=undefined),shareReplay({bufferSize:1,refCount:false}));
    return this.refreshing;
  }
  logout(){this.http.post('/api/auth/logout',{}).pipe(finalize(()=>this.clear())).subscribe({error:()=>{}});}
  clear(){sessionStorage.removeItem('tp-session');this.session.set(null);void this.router.navigateByUrl('/login');}
}
export const authGuard:CanActivateFn=()=>inject(Auth).session()?true:inject(Router).createUrlTree(['/login']);
export const authInterceptor:HttpInterceptorFn=(request,next)=>{
  const auth=inject(Auth); const isApi=request.url.startsWith('/api/');
  const isAuth=['/api/auth/login','/api/auth/register','/api/auth/refresh'].includes(request.url);
  const token=auth.session()?.accessToken;
  const signed=isApi&&!isAuth&&token?request.clone({setHeaders:{Authorization:'Bearer '+token}}):request;
  return next(signed).pipe(catchError((e:HttpErrorResponse)=>{
    if(e.status!==401||!isApi||isAuth||!auth.session())return throwError(()=>e);
    return auth.refresh().pipe(switchMap(s=>next(request.clone({setHeaders:{Authorization:'Bearer '+s.accessToken}}))));
  }));
};
@Injectable({providedIn:'root'})
export class Api {
  private http=inject(HttpClient);
  get<T>(path:string){return this.http.get<T>('/api/'+path);}
  post<T>(path:string,body:unknown){return this.http.post<T>('/api/'+path,body);}
  delete(path:string){return this.http.delete('/api/'+path);}
  read(id:number){return this.http.patch('/api/notifications/'+id+'/read',{});}
}
export function errorMessage(e:unknown){const err=e as HttpErrorResponse;return err.status===0?'Cannot reach the backend. Check that TradePulse is running.':err.error?.message||'The request could not be completed. Please try again.';}

export interface DatasetInfo { enabled:boolean; source:string; sourceUrl:string; from:string; to:string; replayFrom:string; replayTo:string; notes:string; instruments:{symbol:string;dailyVolatilityPercent:number}[]; }
