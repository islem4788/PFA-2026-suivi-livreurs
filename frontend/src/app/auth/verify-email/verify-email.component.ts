import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './verify-email.component.html',
  styleUrls: ['./verify-email.component.scss']
})
export class VerifyEmailComponent implements OnInit {
  form: FormGroup;
  loading = signal<boolean>(false);
  resending = signal<boolean>(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);
  email = signal<string>('');
  livreurId = signal<string | null>(null);
  countdown = signal<number>(0);

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group({
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.email.set(params['email'] || '');
      this.livreurId.set(params['livreurId'] || null);
      if (!params['email']) {
        this.router.navigate(['/signup']);
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.auth.verifyEmail(this.email(), this.form.value.code).subscribe({
      next: () => {
        this.loading.set(false);
        this.auth.redirectAfterLogin();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Code invalide ou expiré');
      }
    });
  }

  resend(): void {
    if (this.countdown() > 0) return;

    this.resending.set(true);
    this.auth.resendVerification(this.email()).subscribe({
      next: () => {
        this.resending.set(false);
        this.success.set('Code renvoyé !');
        this.startCountdown();
        setTimeout(() => this.success.set(null), 3000);
      },
      error: () => {
        this.resending.set(false);
        this.error.set('Erreur lors du renvoi');
      }
    });
  }

  private startCountdown(): void {
    this.countdown.set(60);
    const interval = setInterval(() => {
      this.countdown.update(v => v - 1);
      if (this.countdown() <= 0) clearInterval(interval);
    }, 1000);
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.hasError(error) && ctrl.touched);
  }
}
