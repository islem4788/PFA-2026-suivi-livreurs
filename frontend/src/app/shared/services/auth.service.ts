import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, SignupRequest, SigninRequest, AuthResponse } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'oins_token';
  private readonly REFRESH_KEY = 'oins_refresh';
  private readonly USER_KEY = 'oins_user';

  private currentUserSignal = signal<User | null>(this.loadUser());
  public currentUser = computed(() => this.currentUserSignal());
  public isAuthenticated = computed(() => !!this.currentUserSignal() && !!this.getToken());
  public role = computed(() => this.currentUserSignal()?.role ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  signup(data: SignupRequest): Observable<{ message: string; email: string; livreurId?: string }> {
    return this.http.post<{ message: string; email: string; livreurId?: string }>(
      `${environment.apiUrl}/auth/signup`,
      data
    ).pipe(
      catchError(err => throwError(() => err))
    );
  }

  signin(data: SigninRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${environment.apiUrl}/auth/signin`,
      data
    ).pipe(
      tap(res => this.handleAuthSuccess(res)),
      catchError(err => throwError(() => err))
    );
  }

  verifyEmail(email: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${environment.apiUrl}/auth/verify-email`,
      { email, code }
    ).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  resendVerification(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${environment.apiUrl}/auth/resend-verification`,
      { email }
    );
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${environment.apiUrl}/auth/forgot-password`,
      { email }
    );
  }

  resetPassword(email: string, code: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${environment.apiUrl}/auth/reset-password`,
      { email, code, newPassword }
    );
  }

  signout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSignal.set(null);
    this.router.navigate(['/welcome']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private handleAuthSuccess(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    localStorage.setItem(this.REFRESH_KEY, res.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(res.user));
    this.currentUserSignal.set(res.user);
  }

  private loadUser(): User | null {
    const data = localStorage.getItem(this.USER_KEY);
    return data ? JSON.parse(data) : null;
  }

  redirectAfterLogin(): void {
    const role = this.role();
    if (role === 'BOSS') this.router.navigate(['/boss/map']);
    else if (role === 'LIVREUR') this.router.navigate(['/livreur/dashboard']);
    else this.router.navigate(['/welcome']);
  }
}
