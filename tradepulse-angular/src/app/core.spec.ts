import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Auth, authInterceptor } from './core';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

describe('API authentication',()=>{
  let http:HttpClient;let mock:HttpTestingController;let auth:Auth;
  beforeEach(()=>{sessionStorage.clear();TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter([])]});http=TestBed.inject(HttpClient);mock=TestBed.inject(HttpTestingController);auth=TestBed.inject(Auth);auth.session.set({accessToken:'old',refreshToken:'refresh',email:'test@example.com',userId:1});});
  afterEach(()=>{mock.verify();sessionStorage.clear();});
  it('attaches a token only to the application API',()=>{http.get('/api/alerts').subscribe();expect(mock.expectOne('/api/alerts').request.headers.get('Authorization')).toBe('Bearer old');http.get('https://example.com/public').subscribe();expect(mock.expectOne('https://example.com/public').request.headers.has('Authorization')).toBe(false);});
  it('shares one refresh request across concurrent 401s and retries both',()=>{
    let completed=0;http.get('/api/alerts').subscribe(()=>completed++);http.get('/api/notifications').subscribe(()=>completed++);
    mock.expectOne('/api/alerts').flush({}, {status:401,statusText:'Unauthorized'});mock.expectOne('/api/notifications').flush({}, {status:401,statusText:'Unauthorized'});
    const refresh=mock.expectOne('/api/auth/refresh');expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({accessToken:'new',refreshToken:'rotated',email:'test@example.com',userId:1});
    const a=mock.expectOne('/api/alerts'),b=mock.expectOne('/api/notifications');expect(a.request.headers.get('Authorization')).toBe('Bearer new');expect(b.request.headers.get('Authorization')).toBe('Bearer new');a.flush([]);b.flush([]);expect(completed).toBe(2);
  });
  it('does not refresh a failed login',()=>{auth.login('wrong@example.com','password').subscribe({error:()=>{}});mock.expectOne('/api/auth/login').flush({}, {status:401,statusText:'Unauthorized'});mock.expectNone('/api/auth/refresh');});
});
