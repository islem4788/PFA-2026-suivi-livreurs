import { Routes } from '@angular/router';
import { authGuard } from './shared/guards/auth.guard';
import { roleGuard } from './shared/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/splash', pathMatch: 'full' },

  // Splash screen
  {
    path: 'splash',
    loadComponent: () => import('./auth/splash/splash.component').then(m => m.SplashComponent)
  },

  // Public routes
  {
    path: 'welcome',
    loadComponent: () => import('./auth/welcome/welcome.component').then(m => m.WelcomeComponent)
  },
  {
    path: 'signin',
    loadComponent: () => import('./auth/signin/signin.component').then(m => m.SigninComponent)
  },
  {
    path: 'signup',
    loadComponent: () => import('./auth/signup/signup.component').then(m => m.SignupComponent)
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./auth/verify-email/verify-email.component').then(m => m.VerifyEmailComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent)
  },

  // Client (public — pas de connexion requise)
  {
    path: 'avis-client',
    loadComponent: () => import('./client/avis/avis.component').then(m => m.AvisComponent)
  },

  // Livreur routes (protégées)
  {
    path: 'livreur',
    canActivate: [authGuard, roleGuard],
    data: { role: 'LIVREUR' },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./livreur/dashboard/dashboard.component').then(m => m.LivreurDashboardComponent)
      },
      {
        path: 'signaler',
        loadComponent: () => import('./livreur/signaler/signaler.component').then(m => m.SignalerComponent)
      }
    ]
  },

  // Boss routes (protégées)
  {
    path: 'boss',
    canActivate: [authGuard, roleGuard],
    data: { role: 'BOSS' },
    children: [
      { path: '', redirectTo: 'map', pathMatch: 'full' },
      {
        path: 'map',
        loadComponent: () => import('./boss/map/map.component').then(m => m.BossMapComponent)
      },
      {
        path: 'alertes',
        loadComponent: () => import('./boss/alertes/alertes.component').then(m => m.AlertesComponent)
      },
      {
        path: 'livreurs',
        loadComponent: () => import('./boss/livreurs/livreurs.component').then(m => m.LivreursComponent)
      },
      {
        path: 'avis-clients',
        loadComponent: () => import('./boss/avis-clients/avis-clients.component').then(m => m.AvisClientsComponent)
      }, 
      {
        path: 'scoring',
        loadComponent: () => import('./boss/scoring/scoring.component').then(m => m.BossScoringComponent)
      }


    ]
  },

  { path: '**', redirectTo: '/welcome' }
];
