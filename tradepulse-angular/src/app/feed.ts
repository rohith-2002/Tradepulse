import { Injectable, inject, signal } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import { Auth, Tick } from './core';
@Injectable({providedIn:'root'})
export class Feed {
  private auth=inject(Auth); private client?:Client; private subscription?:StompSubscription;
  status=signal('Disconnected');
  watch(symbol:string,onTick:(tick:Tick)=>void){
    this.stop();this.status.set('Connecting');
    const client=new Client({brokerURL:`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws-native`,reconnectDelay:5000,heartbeatIncoming:10000,heartbeatOutgoing:10000});
    client.beforeConnect=async()=>{client.connectHeaders={Authorization:'Bearer '+this.auth.session()?.accessToken};};
    client.onConnect=()=>{this.status.set('Connected');this.subscription=client.subscribe('/topic/market/'+symbol,m=>{try{onTick(JSON.parse(m.body));}catch{this.status.set('Invalid feed message');}});};
    client.onWebSocketClose=()=>this.status.set('Reconnecting');
    client.onStompError=()=>this.status.set('Connection rejected — sign in again');
    this.client=client;client.activate();
  }
  stop(){this.subscription?.unsubscribe();this.subscription=undefined;void this.client?.deactivate();this.client=undefined;this.status.set('Disconnected');}
}
