import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TrackingService } from '../../shared/services/tracking.service';

@Component({
  selector: 'app-avis',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './avis.component.html',
  styleUrls: ['./avis.component.scss']
})
export class AvisComponent {
  form: FormGroup;
  loading = signal<boolean>(false);
  success = signal<boolean>(false);
  error = signal<string | null>(null);
  rating = signal<number>(0);

  constructor(
    private fb: FormBuilder,
    private tracking: TrackingService,
    private router: Router
  ) {
    this.form = this.fb.group({
      packageId: ['', [Validators.required, Validators.pattern(/^[A-Z0-9-]+$/i)]],
      clientName: ['', [Validators.required, Validators.minLength(3)]],
      clientAddress: ['', [Validators.required, Validators.minLength(5)]],
      comment: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  setRating(stars: number): void {
    this.rating.set(stars);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const feedback = {
      ...this.form.value,
      rating: this.rating() || undefined
    };

    this.tracking.submitFeedback(feedback).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Erreur lors de l\'envoi');
      }
    });
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.hasError(error) && ctrl.touched);
  }

  newFeedback(): void {
    this.success.set(false);
    this.form.reset();
    this.rating.set(0);
  }
}
