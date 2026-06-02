import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {
  step = signal<'email' | 'reset'>('email');
  loading = signal<boolean>(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);

  emailForm: FormGroup;
  resetForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.emailForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });

    this.resetForm = this.fb.group({
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  sendResetEmail(): void {
    if (this.emailForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.auth.forgotPassword(this.emailForm.value.email).subscribe({
      next: () => {
        this.loading.set(false);
        this.step.set('reset');
        this.success.set('Email envoyé ! Vérifiez votre boîte mail');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Erreur — vérifiez l\'email');
      }
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.auth.resetPassword(
      this.emailForm.value.email,
      this.resetForm.value.code,
      this.resetForm.value.newPassword
    ).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/signin'], {
          queryParams: { reset: 'success' }
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Code invalide');
      }
    });
  }
}
