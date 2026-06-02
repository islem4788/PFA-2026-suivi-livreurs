import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TrackingService } from '../../shared/services/tracking.service';
import { GeolocationService } from '../../shared/services/geolocation.service';
import { AuthService } from '../../shared/services/auth.service';
import { TrafficReport } from '../../shared/models/tracking.model';

@Component({
  selector: 'app-signaler',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './signaler.component.html',
  styleUrls: ['./signaler.component.scss']
})
export class SignalerComponent {
  form: FormGroup;
  loading = signal<boolean>(false);
  success = signal<boolean>(false);
  error = signal<string | null>(null);

  types = [
    { id: 'JAM', label: 'Embouteillage', icon: '🚗', color: '#F59E0B' },
    { id: 'ACCIDENT', label: 'Accident', icon: '💥', color: '#EF4444' },
    { id: 'ROAD_BLOCKED', label: 'Route bloquée', icon: '🚧', color: '#DC2626' },
    { id: 'CONSTRUCTION', label: 'Travaux', icon: '🔨', color: '#3B82F6' }
  ];

  constructor(
    private fb: FormBuilder,
    private tracking: TrackingService,
    private geo: GeolocationService,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      type: ['', Validators.required],
      description: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  selectType(type: string): void {
    this.form.patchValue({ type });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const pos = this.geo.currentPosition();
    if (!pos) {
      this.error.set('Position GPS requise pour signaler un incident');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const user = this.auth.currentUser();
    const report: TrafficReport = {
      livreurId: user?.livreurId || user?.id || '',
      type: this.form.value.type,
      description: this.form.value.description,
      latitude: pos.latitude,
      longitude: pos.longitude
    };

    this.tracking.reportTraffic(report).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/livreur/dashboard']), 1500);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Erreur lors de l\'envoi');
      }
    });
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.hasError(error) && ctrl.touched);
  }

  isSelected(type: string): boolean {
    return this.form.value.type === type;
  }
}
