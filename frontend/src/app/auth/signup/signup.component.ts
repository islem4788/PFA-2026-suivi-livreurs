import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, AbstractControl } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { UserRole } from '../../shared/models/user.model';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.scss']
})
export class SignupComponent {
  form: FormGroup;
  loading = signal<boolean>(false);
  error = signal<string | null>(null);
  emailServerError = signal<string | null>(null);
  livreurIdServerError = signal<string | null>(null);
  selectedRole = signal<UserRole>('LIVREUR');
  showPassword = signal<boolean>(false);
  showConfirm = signal<boolean>(false);

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      livreurId: [''],
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      birthDate: ['', [Validators.required, this.adultValidator]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8), this.passwordStrength]],
      confirmPassword: ['', [Validators.required]],
      role: ['LIVREUR' as UserRole, [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.form.get('role')?.valueChanges.subscribe(role => {
      this.selectedRole.set(role);
      const idCtrl = this.form.get('livreurId');
      if (role === 'LIVREUR') {
        idCtrl?.setValidators([Validators.required, Validators.pattern(/^LIV-\d{3,}$/)]);
      } else {
        idCtrl?.clearValidators();
      }
      idCtrl?.updateValueAndValidity();
    });

    this.form.get('livreurId')?.setValidators([
      Validators.required,
      Validators.pattern(/^LIV-\d{3,}$/)
    ]);
    this.form.get('livreurId')?.updateValueAndValidity();
  }

  selectRole(role: UserRole): void {
    this.form.patchValue({ role });
  }

  togglePassword(): void { this.showPassword.set(!this.showPassword()); }
  toggleConfirm(): void { this.showConfirm.set(!this.showConfirm()); }

  adultValidator(control: AbstractControl): { [key: string]: boolean } | null {
    if (!control.value) return null;
    const birthDate = new Date(control.value);
    const today = new Date();
    const age = today.getFullYear() - birthDate.getFullYear();
    const monthDiff = today.getMonth() - birthDate.getMonth();
    const realAge = monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())
      ? age - 1 : age;
    return realAge >= 18 ? null : { tooYoung: true };
  }

  passwordStrength(control: AbstractControl): { [key: string]: boolean } | null {
    if (!control.value) return null;
    const value = control.value;
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasNumber = /\d/.test(value);
    return hasUpper && hasLower && hasNumber ? null : { weakPassword: true };
  }

  passwordMatchValidator(group: AbstractControl): { [key: string]: boolean } | null {
    const password = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return password === confirm ? null : { mismatch: true };
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.hasError(error) && ctrl.touched);
  }

  hasMismatch(): boolean {
    return !!(this.form.errors?.['mismatch'] && this.form.get('confirmPassword')?.touched);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.emailServerError.set(null);
    this.livreurIdServerError.set(null);

    const { confirmPassword, ...data } = this.form.value;

    this.auth.signup(data).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.router.navigate(['/verify-email'], {
          queryParams: {
            email: res.email,
            livreurId: res.livreurId
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 409) {
          const msg: string = err.error?.message || '';
          // Le backend renvoie "ID livreur déjà utilisé" ou "Email déjà utilisé"
          if (msg.toLowerCase().includes('livreur')) {
            this.livreurIdServerError.set('Cet ID livreur est déjà utilisé par un autre compte');
            this.form.get('livreurId')?.setErrors({ serverError: true });
          } else {
            this.emailServerError.set('Cet email est déjà associé à un compte existant');
            this.form.get('email')?.setErrors({ serverError: true });
          }
        } else {
          this.error.set(err.error?.message || 'Erreur lors de l\'inscription');
        }
      }
    });
  }
}
