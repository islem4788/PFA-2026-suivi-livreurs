import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { GeolocationService, GpsPosition } from '../../shared/services/geolocation.service';
import { WebsocketService } from '../../shared/services/websocket.service';
import { TrackingService } from '../../shared/services/tracking.service';
import { AuthService } from '../../shared/services/auth.service';
import { DrivingState } from '../../shared/models/tracking.model';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-livreur-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class LivreurDashboardComponent implements OnInit, OnDestroy {
  private subs: Subscription[] = [];

  position = signal<GpsPosition | null>(null);
  drivingState = signal<DrivingState>('NORMAL');
  speed = signal<number>(0);
  isTracking = signal<boolean>(false);
  gpsError = signal<string | null>(null);
  uptime = signal<string>('00:00');
  startTime = Date.now();
  codeCopied = signal<boolean>(false);

  constructor(
    private geo: GeolocationService,
    private ws: WebsocketService,
    private tracking: TrackingService,
    public auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // 1. Demander permission notifications au démarrage
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }

    this.ws.connect();

    // 2. Écouter le drivingState retourné par le backend via WebSocket
    this.subs.push(
      this.ws.positions$.subscribe(pos => {
        const user = this.auth.currentUser();
        if (pos.livreurId === (user?.livreurId || user?.id)) {
          this.drivingState.set(pos.drivingState as DrivingState);
          if (pos.drivingState === 'AGGRESSIVE' || pos.drivingState === 'RISKY') {
            this.showNotification(pos.drivingState as DrivingState);
          }
        }
      })
    );

    // 3. GPS tracking → envoyer position au backend
    this.subs.push(
      this.geo.startTracking().subscribe(pos => {
        this.position.set(pos);
        this.speed.set(pos.speed ? pos.speed * 3.6 : 0);

        const user = this.auth.currentUser();
        if (user && this.ws.connected()) {
          this.ws.sendPosition({
            livreurId: user.livreurId || user.id,
            firstName: user.firstName,
            lastName: user.lastName,
            latitude: pos.latitude,
            longitude: pos.longitude,
            speed: this.speed(),
            heading: pos.heading || 0,
            accuracy: pos.accuracy,
            drivingState: this.drivingState(),
            status: 'ACTIVE'
          });
        }
      })
    );

    // 4. Timer uptime
    this.subs.push(
      interval(1000).subscribe(() => this.updateUptime())
    );

    this.isTracking.set(true);
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.geo.stopTracking();
  }

  private updateUptime(): void {
    const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
    const h = Math.floor(elapsed / 3600).toString().padStart(2, '0');
    const m = Math.floor((elapsed % 3600) / 60).toString().padStart(2, '0');
    const s = (elapsed % 60).toString().padStart(2, '0');
    this.uptime.set(elapsed >= 3600 ? `${h}:${m}:${s}` : `${m}:${s}`);
  }

  // Notifications push livreur
 private showNotification(state: DrivingState): void {
  if (!('Notification' in window)) return;

  const title = state === 'AGGRESSIVE'
    ? '🔴 Conduite dangereuse !'
    : '🟡 Conduite risquée';

  const options = {
    body: state === 'AGGRESSIVE'
      ? 'Ralentissez immédiatement !'
      : 'Adaptez votre conduite',
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-72x72.png',
    tag: 'driving-state'
  };

  if (Notification.permission === 'granted') {
    // Service Worker → notification fond d'écran
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.ready.then(sw => {
        sw.showNotification(title, options);
      });
    } else {
      new Notification(title, options);
    }
  } else {
    Notification.requestPermission().then(perm => {
      if (perm === 'granted') this.showNotification(state);
    });
  }
}

  getStateColor(): string {
    const state = this.drivingState();
    if (state === 'AGGRESSIVE') return '#EF4444';
    if (state === 'RISKY') return '#F59E0B';
    return '#10B981';
  }

  getStateLabel(): string {
    const state = this.drivingState();
    const labels: Record<DrivingState, string> = {
      'NORMAL': 'NORMAL',
      'RISKY': 'RISQUÉ',
      'AGGRESSIVE': 'DANGEREUX'
    };
    return labels[state];
  }

  getStateMessage(): string {
    const state = this.drivingState();
    const msgs: Record<DrivingState, string> = {
      'NORMAL': 'Continuez votre conduite — Tout va bien',
      'RISKY': 'Attention — Adaptez votre conduite',
      'AGGRESSIVE': 'Danger — Ralentissez immédiatement'
    };
    return msgs[state];
  }

  goToSignaler(): void {
    this.router.navigate(['/livreur/signaler']);
  }

  signout(): void {
    this.auth.signout();
  }

  copyCode(code: string): void {
    navigator.clipboard.writeText(code).then(() => {
      this.codeCopied.set(true);
      setTimeout(() => this.codeCopied.set(false), 2000);
    });
  }
}