import { Component, OnInit, OnDestroy, AfterViewInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import * as L from 'leaflet';
import { TrackingService } from '../../shared/services/tracking.service';
import { WebsocketService } from '../../shared/services/websocket.service';
import { AuthService } from '../../shared/services/auth.service';
import { PushNotificationService } from '../../shared/services/push-notification.service';
import { LivreurPosition, DrivingState } from '../../shared/models/tracking.model';
import { Subscription, interval } from 'rxjs';
import { PushService } from '../../shared/services/push.service';

@Component({
  selector: 'app-boss-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss']
})
export class BossMapComponent implements OnInit, AfterViewInit, OnDestroy {
  private map!: L.Map;
  private markers = new Map<string, L.Marker>();
  private subs: Subscription[] = [];

  livreurs = signal<LivreurPosition[]>([]);
  selectedLivreur = signal<LivreurPosition | null>(null);
  unreadAlerts = signal<number>(0);

  totalLivreurs = computed(() => this.livreurs().length);
  activeLivreurs = computed(() => this.livreurs().filter(l => l.status === 'ACTIVE').length);
  alertCount = computed(() => this.livreurs().filter(l => l.status === 'ALERT').length);

  constructor(
    private tracking: TrackingService,
    private ws: WebsocketService,
    public auth: AuthService,
    private push: PushNotificationService,
     private pushService: PushService, 
    private router: Router
  ) {}

  ngOnInit(): void {
    //  Demander permission notifications via Service Worker
    this.pushService.subscribeToPush();

    this.ws.connect();
    this.loadLivreurs();

    this.subs.push(
      this.ws.positions$.subscribe(pos => {
        this.handlePositionUpdate(pos);

        // ✅ Notification push fond d'écran
        if (pos.drivingState === 'AGGRESSIVE') {
          this.push.showLocalNotification(
            `🔴 ${pos.firstName} ${pos.lastName} — Conduite dangereuse !`,
            `Livreur ${pos.livreurId} — Intervention urgente !`,
            `alert-${pos.livreurId}`
          );
          this.unreadAlerts.update(v => v + 1);
        } else if (pos.drivingState === 'RISKY') {
          this.push.showLocalNotification(
            `🟡 ${pos.firstName} ${pos.lastName} — Conduite risquée`,
            `Livreur ${pos.livreurId} — Surveillance recommandée`,
            `alert-${pos.livreurId}`
          );
          this.unreadAlerts.update(v => v + 1);
        }
      })
    );

    this.subs.push(
      this.ws.alerts$.subscribe(() => {
        this.unreadAlerts.update(v => v + 1);
      })
    );

    // ✅ Polling toutes les 10 secondes
    this.subs.push(
      interval(10000).subscribe(() => this.loadLivreurs())
    );
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initMap(), 100);
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.ws.disconnect();
    if (this.map) this.map.remove();
  }

  private initMap(): void {
    this.map = L.map('boss-map', {
      center: [36.8065, 10.1815],
      zoom: 12,
      zoomControl: true,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap'
    }).addTo(this.map);

    L.control.attribution({ prefix: 'OINSExpress' }).addTo(this.map);

    this.refreshMarkers();
  }

  private loadLivreurs(): void {
    this.tracking.getAllLivreurs().subscribe({
      next: (data) => {
        this.livreurs.set(data);
        this.refreshMarkers();
      },
      error: (err) => console.error('Erreur chargement livreurs', err)
    });
  }

  private handlePositionUpdate(pos: LivreurPosition): void {
    this.livreurs.update(list => {
      const idx = list.findIndex(l => l.livreurId === pos.livreurId);
      if (idx >= 0) {
        const updated = [...list];
        updated[idx] = pos;
        return updated;
      }
      return [...list, pos];
    });
    if (this.selectedLivreur()?.livreurId === pos.livreurId) {
      this.selectedLivreur.set(pos);
    }
    this.updateMarker(pos);
  }

  private refreshMarkers(): void {
    if (!this.map) return;
    this.livreurs().forEach(l => this.updateMarker(l));
  }

  private updateMarker(pos: LivreurPosition): void {
    if (!this.map) return;

    const existing = this.markers.get(pos.livreurId);
    const icon = this.getIconForState(pos.drivingState, pos.status);
    const latLng = L.latLng(pos.latitude, pos.longitude);

    if (existing) {
      existing.setLatLng(latLng);
      existing.setIcon(icon);
      existing.setPopupContent(this.buildPopup(pos));
    } else {
      const marker = L.marker(latLng, { icon })
        .bindPopup(this.buildPopup(pos))
        .on('click', () => this.selectLivreur(pos));
      marker.addTo(this.map);
      this.markers.set(pos.livreurId, marker);
    }
  }

  private getIconForState(state: DrivingState, status: string): L.DivIcon {
    let color = '#10B981';
    let pulse = '';

    if (status === 'ALERT' || state === 'AGGRESSIVE') {
      color = '#EF4444';
      pulse = 'pulse-red';
    } else if (state === 'RISKY') {
      color = '#F59E0B';
      pulse = 'pulse-amber';
    } else if (status === 'OFFLINE') {
      color = '#888888';
    }

    const html = `
      <div class="livreur-marker ${pulse}">
        <div class="marker-pin" style="background:${color}">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M3 17l6-6 4 4 8-8" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="marker-shadow"></div>
      </div>
    `;

    return L.divIcon({
      html,
      className: '',
      iconSize: [40, 50],
      iconAnchor: [20, 50],
      popupAnchor: [0, -50]
    });
  }

  private buildPopup(pos: LivreurPosition): string {
    return `
      <div class="popup-content">
        <strong>${pos.firstName} ${pos.lastName}</strong><br>
        <small>${pos.livreurId}</small><br>
        <div style="margin-top:6px">
          <span>🚗 ${pos.speed.toFixed(0)} km/h</span><br>
          <span>État : ${this.translateState(pos.drivingState)}</span>
        </div>
      </div>
    `;
  }

  selectLivreur(livreur: LivreurPosition): void {
    this.selectedLivreur.set(livreur);
    this.map.setView([livreur.latitude, livreur.longitude], 15);
  }

  closePanel(): void {
    this.selectedLivreur.set(null);
  }

  goToAlerts(): void {
    this.unreadAlerts.set(0);
    this.router.navigate(['/boss/alertes']);
  }

  goToList(): void {
    this.router.navigate(['/boss/livreurs']);
  }

  goToAvisClients(): void {
    this.router.navigate(['/boss/avis-clients']);
  }

  goToScoring(): void {
  this.router.navigate(['/boss/scoring']);
}

  signout(): void {
    this.auth.signout();
  }

  translateState(state: DrivingState): string {
    const map = { 'NORMAL': 'Normal', 'RISKY': 'Risqué', 'AGGRESSIVE': 'Dangereux' };
    return map[state] || state;
  }

  translateStatus(status: string): string {
    const map: Record<string, string> = {
      'ACTIVE': 'En activité',
      'IDLE': 'Au repos',
      'OFFLINE': 'Hors ligne',
      'ALERT': 'ALERTE'
    };
    return map[status] || status;
  }

  getStatusColor(state: DrivingState, status: string): string {
    if (status === 'ALERT' || state === 'AGGRESSIVE') return '#EF4444';
    if (state === 'RISKY') return '#F59E0B';
    if (status === 'OFFLINE') return '#888';
    return '#10B981';
  }

  formatSensor(value: number | null | undefined, digits = 3): string {
    return typeof value === 'number' ? value.toFixed(digits) : '--';
  }
}
