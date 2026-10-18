import { Component, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { Auth, authGuard, authInterceptor } from './app/core';
@Component({selector:'tp-root',standalone:true,imports:[RouterOutlet,RouterLink,RouterLinkActive],template:`
@if(auth.session()) {<aside><a class="brand" routerLink="/dashboard"><span>◈</span> TradePulse</a><p class="nav-label">WORKSPACE</p><nav><a routerLink="/dashboard" routerLinkActive="active"><span>▦</span> Overview</a><a routerLink="/alerts" routerLinkActive="active"><span>◉</span> Alerts</a><a routerLink="/backtest" routerLinkActive="active"><span>⌁</span> Strategy lab</a><a routerLink="/notifications" routerLinkActive="active"><span>☷</span> Notifications</a></nav><div class="sidebar-bottom"><span class="badge">RESEARCH PLATFORM</span><p>Observe. Test. Understand.</p><small>TradePulse 1.0</small></div></aside><div class="workspace"><header><span>Workspace <span class="muted">/ Research desk</span></span><div><span>{{auth.session()?.email}}</span><button (click)="auth.logout()">Sign out</button></div></header><main><router-outlet/></main><footer>TradePulse · Strategy research & simulation · No order execution</footer></div>} @else {<router-outlet/>}`})
class App {auth=inject(Auth);}
bootstrapApplication(App,{providers:[provideHttpClient(withInterceptors([authInterceptor])),provideRouter([
  {path:'login',loadComponent:()=>import('./app/pages').then(m=>m.Login)},
  {path:'dashboard',canActivate:[authGuard],loadComponent:()=>import('./app/pages').then(m=>m.Dashboard)},
  {path:'alerts',canActivate:[authGuard],loadComponent:()=>import('./app/pages').then(m=>m.Alerts)},
  {path:'backtest',canActivate:[authGuard],loadComponent:()=>import('./app/pages').then(m=>m.Backtests)},
  {path:'notifications',canActivate:[authGuard],loadComponent:()=>import('./app/pages').then(m=>m.Notifications)},
  {path:'**',redirectTo:'dashboard'}
])]}).catch(console.error);
