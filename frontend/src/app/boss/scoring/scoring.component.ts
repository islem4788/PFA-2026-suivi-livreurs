import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { environment } from '../../../environments/environment';

interface LivreurScore {
  livreurId: string;
  firstName: string;
  lastName: string;
  rang: number;
  scoreFinal: number;
  scoreConduite: number;
  scoreAvis: number;
  scorePresence: number;
  scoreAlertes: number;
  evolutionScore: number;
  tendance: 'UP' | 'DOWN' | 'STABLE';
  totalPositions: number;
  positionsNormal: number;
  positionsRisky: number;
  positionsAggressive: number;
  moyenneAvis: number;
  nombreAvis: number;
  nombreAlertes: number;
  joursConnecte: number;
  badge: string;
  badgeColor: string;
}

@Component({
  selector: 'app-boss-scoring',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="scoring-container">

      <!-- Bouton retour -->
      <button class="back-btn" (click)="goBack()">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M19 12H5M12 5l-7 7 7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        Retour
      </button>

      <!-- Header -->
      <div class="scoring-header">
        <div class="header-left">
          <h1>🏆 Classement des livreurs</h1>
          <p class="subtitle">Mis à jour chaque semaine • Basé sur conduite, avis clients et présence</p>
        </div>
        <div class="period-selector">
          <button [class.active]="periode() === 1" (click)="changerPeriode(1)">Ce mois</button>
          <button [class.active]="periode() === 3" (click)="changerPeriode(3)">3 mois</button>
          <button [class.active]="periode() === 12" (click)="changerPeriode(12)">12 mois</button>
        </div>
      </div>

      <!-- Chargement -->
      @if (loading()) {
        <div class="loading">
          <div class="spinner"></div>
          <p>Calcul du classement IA...</p>
        </div>
      }

      <!-- Podium top 3 -->
      @if (!loading() && scores().length >= 2) {
        <div class="podium">
          @if (scores().length >= 2) {
          <div class="podium-card silver">
            <div class="podium-rang">🥈</div>
            <div class="avatar">{{ scores()[1].firstName[0] }}</div>
            <div class="podium-name">{{ scores()[1].firstName }}</div>
            <div class="podium-score">{{ scores()[1].scoreFinal }}</div>
            <div class="podium-bar silver-bar"></div>
          </div>
          }
          <div class="podium-card gold">
            <div class="podium-rang">🥇</div>
            <div class="avatar gold-avatar">{{ scores()[0].firstName[0] }}</div>
            <div class="podium-name">{{ scores()[0].firstName }}</div>
            <div class="podium-score">{{ scores()[0].scoreFinal }}</div>
            <div class="podium-bar gold-bar"></div>
          </div>
          @if (scores().length >= 3) {
          <div class="podium-card bronze">
            <div class="podium-rang">🥉</div>
            <div class="avatar">{{ scores()[2].firstName[0] }}</div>
            <div class="podium-name">{{ scores()[2].firstName }}</div>
            <div class="podium-score">{{ scores()[2].scoreFinal }}</div>
            <div class="podium-bar bronze-bar"></div>
          </div>
          }
        </div>
      }

      <!-- Liste classement -->
      @if (!loading() && scores().length > 0) {
        <div class="classement-list">
          @for (s of scores(); track s.livreurId) {
            <div class="livreur-card" [class.top1]="s.rang === 1">

              <div class="card-left">
                <div class="rang-badge" [style.background]="getRangColor(s.rang)">{{ s.rang }}</div>
                <div class="livreur-info">
                  <div class="livreur-nom">{{ s.firstName }} {{ s.lastName }}</div>
                  <div class="livreur-id">{{ s.livreurId }}</div>
                  <div class="badge-pill"
                       [style.background]="s.badgeColor + '20'"
                       [style.color]="s.badgeColor"
                       [style.border]="'1px solid ' + s.badgeColor">
                    {{ s.badge }}
                  </div>
                </div>
              </div>

              <div class="card-center">
                <div class="score-circle" [style.border-color]="getScoreColor(s.scoreFinal)">
                  <span class="score-value">{{ s.scoreFinal }}</span>
                  <span class="score-label">/100</span>
                </div>
                <div class="evolution"
                     [class.up]="s.tendance === 'UP'"
                     [class.down]="s.tendance === 'DOWN'"
                     [class.stable]="s.tendance === 'STABLE'">
                  {{ s.tendance === 'UP' ? '↑' : s.tendance === 'DOWN' ? '↓' : '→' }}
                  {{ s.evolutionScore > 0 ? '+' : '' }}{{ s.evolutionScore }}
                </div>
              </div>

              <div class="card-right">
                <div class="sub-score">
                  <span class="sub-label">🚗 Conduite</span>
                  <div class="progress-bar">
                    <div class="progress-fill"
                         [style.width]="s.scoreConduite + '%'"
                         [style.background]="getScoreColor(s.scoreConduite)"></div>
                  </div>
                  <span class="sub-value">{{ s.scoreConduite }}</span>
                </div>
                <div class="sub-score">
                  <span class="sub-label">⭐ Avis</span>
                  <div class="progress-bar">
                    <div class="progress-fill"
                         [style.width]="s.scoreAvis + '%'"
                         [style.background]="getScoreColor(s.scoreAvis)"></div>
                  </div>
                  <span class="sub-value">{{ s.scoreAvis }}</span>
                </div>
                <div class="sub-score">
                  <span class="sub-label">📅 Présence</span>
                  <div class="progress-bar">
                    <div class="progress-fill"
                         [style.width]="s.scorePresence + '%'"
                         [style.background]="getScoreColor(s.scorePresence)"></div>
                  </div>
                  <span class="sub-value">{{ s.scorePresence }}</span>
                </div>
                <div class="sub-score">
                  <span class="sub-label">🔔 Alertes</span>
                  <div class="progress-bar">
                    <div class="progress-fill"
                         [style.width]="s.scoreAlertes + '%'"
                         [style.background]="getScoreColor(s.scoreAlertes)"></div>
                  </div>
                  <span class="sub-value">{{ s.scoreAlertes }}</span>
                </div>
              </div>

              <div class="card-stats">
                <div class="stat">
                  <span class="stat-value">{{ s.nombreAvis }}</span>
                  <span class="stat-label">Avis</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ s.nombreAlertes }}</span>
                  <span class="stat-label">Alertes</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ s.joursConnecte }}j</span>
                  <span class="stat-label">Actif</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ s.moyenneAvis > 0 ? s.moyenneAvis : '—' }}</span>
                  <span class="stat-label">Moy. ⭐</span>
                </div>
              </div>

            </div>
          }
        </div>
      }

      <!-- Aucun livreur -->
      @if (!loading() && scores().length === 0) {
        <div class="empty-state">
          <p>Aucun livreur dans votre équipe ou pas encore de données.</p>
        </div>
      }

    </div>
  `,
  styles: [`
    .scoring-container {
      padding: 16px;
      max-width: 900px;
      margin: 0 auto;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    /* ✅ Bouton retour */
    .back-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      background: none;
      border: none;
      color: #64748b;
      font-size: 14px;
      cursor: pointer;
      padding: 8px 0;
      margin-bottom: 16px;
      transition: color 0.2s;
    }
    .back-btn:hover { color: #f97316; }

    .scoring-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      flex-wrap: wrap;
      gap: 12px;
    }

    h1 { margin: 0; font-size: 22px; color: #1e293b; }
    .subtitle { margin: 4px 0 0; font-size: 13px; color: #64748b; }

    .period-selector { display: flex; gap: 8px; }
    .period-selector button {
      padding: 6px 14px;
      border: 1px solid #e2e8f0;
      border-radius: 20px;
      background: white;
      color: #64748b;
      cursor: pointer;
      font-size: 13px;
      transition: all 0.2s;
    }
    .period-selector button.active {
      background: #f97316;
      color: white;
      border-color: #f97316;
    }

    .loading { text-align: center; padding: 48px; color: #64748b; }
    .spinner {
      width: 40px; height: 40px;
      border: 3px solid #e2e8f0;
      border-top-color: #f97316;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto 12px;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .podium {
      display: flex;
      justify-content: center;
      align-items: flex-end;
      gap: 12px;
      margin-bottom: 32px;
      padding: 24px 16px 0;
    }
    .podium-card {
      display: flex; flex-direction: column;
      align-items: center; gap: 6px; min-width: 90px;
    }
    .podium-rang { font-size: 24px; }
    .avatar {
      width: 48px; height: 48px; border-radius: 50%;
      background: #e2e8f0; color: #475569;
      display: flex; align-items: center; justify-content: center;
      font-weight: bold; font-size: 18px;
    }
    .gold-avatar { background: #fef3c7; color: #92400e; }
    .podium-name { font-weight: 600; font-size: 13px; color: #1e293b; }
    .podium-score { font-size: 20px; font-weight: bold; color: #f97316; }
    .podium-bar { width: 80px; border-radius: 4px 4px 0 0; }
    .gold-bar   { height: 60px; background: #f59e0b; }
    .silver-bar { height: 40px; background: #94a3b8; }
    .bronze-bar { height: 25px; background: #b45309; }

    .livreur-card {
      display: flex; align-items: center; gap: 16px;
      background: white; border: 1px solid #e2e8f0;
      border-radius: 12px; padding: 16px;
      margin-bottom: 12px; transition: box-shadow 0.2s;
      flex-wrap: wrap;
    }
    .livreur-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
    .livreur-card.top1 {
      border-color: #f59e0b;
      background: linear-gradient(135deg, #fffbeb 0%, white 100%);
    }

    .card-left { display: flex; align-items: center; gap: 12px; min-width: 160px; }
    .rang-badge {
      width: 36px; height: 36px; border-radius: 50%;
      color: white; display: flex; align-items: center;
      justify-content: center; font-weight: bold; font-size: 16px; flex-shrink: 0;
    }
    .livreur-nom { font-weight: 600; font-size: 15px; color: #1e293b; }
    .livreur-id  { font-size: 12px; color: #94a3b8; }
    .badge-pill {
      display: inline-block; padding: 2px 8px;
      border-radius: 12px; font-size: 11px;
      font-weight: 600; margin-top: 4px;
    }

    .card-center {
      display: flex; flex-direction: column;
      align-items: center; gap: 4px; min-width: 80px;
    }
    .score-circle {
      width: 64px; height: 64px; border-radius: 50%; border: 3px solid;
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
    }
    .score-value { font-size: 20px; font-weight: bold; color: #1e293b; line-height: 1; }
    .score-label { font-size: 10px; color: #94a3b8; }
    .evolution { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
    .evolution.up     { color: #10b981; background: #d1fae5; }
    .evolution.down   { color: #ef4444; background: #fee2e2; }
    .evolution.stable { color: #64748b; background: #f1f5f9; }

    .card-right { flex: 1; min-width: 200px; display: flex; flex-direction: column; gap: 6px; }
    .sub-score  { display: flex; align-items: center; gap: 8px; }
    .sub-label  { font-size: 11px; color: #64748b; min-width: 70px; }
    .progress-bar {
      flex: 1; height: 6px; background: #e2e8f0;
      border-radius: 3px; overflow: hidden;
    }
    .progress-fill { height: 100%; border-radius: 3px; transition: width 0.6s ease; }
    .sub-value { font-size: 11px; font-weight: 600; color: #1e293b; min-width: 28px; text-align: right; }

    .card-stats {
      display: grid; grid-template-columns: 1fr 1fr;
      gap: 8px; min-width: 100px;
    }
    .stat { text-align: center; }
    .stat-value { display: block; font-size: 16px; font-weight: bold; color: #1e293b; }
    .stat-label { font-size: 10px; color: #94a3b8; }

    .empty-state { text-align: center; padding: 48px; color: #94a3b8; }
  `]
})
export class BossScoringComponent implements OnInit {

  scores  = signal<LivreurScore[]>([]);
  loading = signal(true);
  periode = signal(1);

  constructor(
    private http: HttpClient,
    public auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.chargerClassement();
  }

  goBack(): void {
    this.router.navigate(['/boss/map']);
  }

  changerPeriode(mois: number): void {
    this.periode.set(mois);
    this.chargerClassement();
  }

  private chargerClassement(): void {
    this.loading.set(true);
    const token = this.auth.getToken();
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });

    this.http.get<LivreurScore[]>(
      `${environment.apiUrl}/boss/scoring?mois=${this.periode()}`,
      { headers }
    ).subscribe({
      next: (data) => {
        this.scores.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erreur scoring:', err);
        this.scores.set(this.getDemoData());
        this.loading.set(false);
      }
    });
  }

  getRangColor(rang: number): string {
    if (rang === 1) return '#f59e0b';
    if (rang === 2) return '#94a3b8';
    if (rang === 3) return '#b45309';
    return '#e2e8f0';
  }

  getScoreColor(score: number): string {
    if (score >= 80) return '#10b981';
    if (score >= 65) return '#3b82f6';
    if (score >= 50) return '#f59e0b';
    return '#ef4444';
  }

  private getDemoData(): LivreurScore[] {
    return [
      {
        livreurId: 'LIV-001', firstName: 'Islem', lastName: 'Mrabet',
        rang: 1, scoreFinal: 87.5,
        scoreConduite: 92, scoreAvis: 88, scorePresence: 80, scoreAlertes: 70,
        evolutionScore: 3.2, tendance: 'UP',
        totalPositions: 450, positionsNormal: 400, positionsRisky: 40, positionsAggressive: 10,
        moyenneAvis: 4.4, nombreAvis: 12, nombreAlertes: 3, joursConnecte: 18,
        badge: 'EXCELLENT', badgeColor: '#10B981'
      }
    ];
  }
}
