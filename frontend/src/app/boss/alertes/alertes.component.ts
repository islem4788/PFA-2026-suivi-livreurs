import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TrackingService } from '../../shared/services/tracking.service';
import { Alert } from '../../shared/models/tracking.model';

@Component({
  selector: 'app-alertes',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  templateUrl: './alertes.component.html',
  styleUrls: ['./alertes.component.scss']
})
export class AlertesComponent implements OnInit {
  alerts = signal<Alert[]>([]);
  loading = signal<boolean>(true);
  filter = signal<'all' | 'unresolved' | 'resolved'>('unresolved');

  filteredAlerts = computed(() => {
    const f = this.filter();
    if (f === 'all') return this.alerts();
    if (f === 'resolved') return this.alerts().filter(a => a.resolved);
    return this.alerts().filter(a => !a.resolved);
  });

  constructor(private tracking: TrackingService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.tracking.getAlerts(false).subscribe({
      next: (data) => {
        this.alerts.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  setFilter(f: 'all' | 'unresolved' | 'resolved'): void {
    this.filter.set(f);
  }

  resolveAlert(alert: Alert): void {
    this.tracking.resolveAlert(alert.id).subscribe({
      next: () => this.load()
    });
  }

  goToMap(alert: Alert): void {
    this.router.navigate(['/boss/map'], {
      queryParams: { focus: alert.livreurId }
    });
  }

  getAlertIcon(type: string): string {
    const icons: Record<string, string> = {
      'IMU_ANOMALY': '⚠️',
      'STATIONARY_TIMEOUT': '⏱️',
      'AGGRESSIVE_DRIVING': '🚨',
      'TRAFFIC_REPORT': '🚧',
      'ACCIDENT': '💥',
      'CLIENT_COMPLAINT': '📝'
    };
    return icons[type] || '⚠️';
  }

  getAlertLabel(type: string): string {
    const labels: Record<string, string> = {
      'IMU_ANOMALY': 'Anomalie IMU',
      'STATIONARY_TIMEOUT': 'Arrêt prolongé',
      'AGGRESSIVE_DRIVING': 'Conduite dangereuse',
      'TRAFFIC_REPORT': 'Trafic signalé',
      'ACCIDENT': 'Accident',
      'CLIENT_COMPLAINT': 'Réclamation client'
    };
    return labels[type] || type;
  }

  getSeverityClass(severity: string): string {
    return `severity-${severity.toLowerCase()}`;
  }
}
