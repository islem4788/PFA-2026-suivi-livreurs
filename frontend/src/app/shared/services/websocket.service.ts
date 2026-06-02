import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LivreurPosition, Alert } from '../models/tracking.model';
import { AuthService } from './auth.service';

declare const SockJS: any;
declare const Stomp: any;

@Injectable({ providedIn: 'root' })
export class WebsocketService {
  private client: any = null;
  public connected = signal<boolean>(false);

  private positionsSubject = new Subject<LivreurPosition>();
  public positions$ = this.positionsSubject.asObservable();

  private alertsSubject = new Subject<Alert>();
  public alerts$ = this.alertsSubject.asObservable();

  private reconnectTimer: any = null;

  constructor(private auth: AuthService) {}

  connect(): void {
    if (this.client?.connected) return;

    try {
      const socket = new SockJS(environment.wsUrl);
      this.client = Stomp.over(socket);

      // ✅ Heartbeat pour garder connexion Render active
      this.client.heartbeat.outgoing = 20000;
      this.client.heartbeat.incoming = 20000;

      // Désactiver les logs console de stomp
      this.client.debug = () => {};

      const headers = {
        Authorization: `Bearer ${this.auth.getToken() ?? ''}`
      };

      this.client.connect(
        headers,
        () => {
          this.connected.set(true);
          if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
          }

          this.client.subscribe('/topic/positions', (msg: any) => {
            try {
              this.positionsSubject.next(JSON.parse(msg.body));
            } catch (e) {
              console.error('WebSocket: erreur parsing position', e);
            }
          });

          this.client.subscribe('/topic/alerts', (msg: any) => {
            try {
              this.alertsSubject.next(JSON.parse(msg.body));
            } catch (e) {
              console.error('WebSocket: erreur parsing alert', e);
            }
          });
        },
        (_error: any) => {
          this.connected.set(false);
          this.scheduleReconnect();
        }
      );
    } catch (e) {
      console.warn('WebSocket non disponible', e);
      this.connected.set(false);
    }
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.client?.connected) {
      this.client.disconnect();
      this.connected.set(false);
    }
  }

  sendPosition(position: Partial<LivreurPosition>): void {
    if (this.client?.connected) {
      this.client.send('/app/position', {}, JSON.stringify(position));
    }
  }

  sendTrafficReport(report: any): void {
    if (this.client?.connected) {
      this.client.send('/app/traffic', {}, JSON.stringify(report));
    }
  }

  private scheduleReconnect(): void {
    if (!this.reconnectTimer) {
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        this.connect();
      }, 5000);
    }
  }
}