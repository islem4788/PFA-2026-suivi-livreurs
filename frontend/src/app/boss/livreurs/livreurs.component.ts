import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { TrackingService } from '../../shared/services/tracking.service';
import { BossService } from '../../shared/services/boss.service';
import { LivreurPosition } from '../../shared/models/tracking.model';
import { User } from '../../shared/models/user.model';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-livreurs',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './livreurs.component.html',
  styleUrls: ['./livreurs.component.scss']
})
export class LivreursComponent implements OnInit {
  livreurs = signal<User[]>([]);
  positions = signal<Map<string, LivreurPosition>>(new Map());
  loading = signal<boolean>(true);
  search = signal<string>('');

  showAddModal = signal<boolean>(false);
  addForm: FormGroup;
  adding = signal<boolean>(false);
  addError = signal<string | null>(null);
  addSuccess = signal<string | null>(null);

  livreurToRemove = signal<User | null>(null);
  removing = signal<boolean>(false);

  filtered = computed(() => {
    const q = this.search().toLowerCase().trim();
    if (!q) return this.livreurs();
    return this.livreurs().filter(l =>
      l.firstName.toLowerCase().includes(q) ||
      l.lastName.toLowerCase().includes(q) ||
      (l.livreurId?.toLowerCase().includes(q) ?? false)
    );
  });

  constructor(
    private tracking: TrackingService,
    private boss: BossService,
    private router: Router,
    private fb: FormBuilder
  ) {
    this.addForm = this.fb.group({
      code: ['', [
        Validators.required,
        Validators.pattern(/^LIV-\d{3,}$/i)
      ]]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      livreurs: this.boss.getMyLivreurs(),
      positions: this.tracking.getAllLivreurs()
    }).subscribe({
      next: ({ livreurs, positions }) => {
        this.livreurs.set(livreurs);
        const map = new Map<string, LivreurPosition>();
        positions.forEach(p => map.set(p.livreurId, p));
        this.positions.set(map);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openAddModal(): void {
    this.showAddModal.set(true);
    this.addForm.reset();
    this.addError.set(null);
    this.addSuccess.set(null);
  }

  closeAddModal(): void {
    this.showAddModal.set(false);
  }

  submitAdd(): void {
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      return;
    }
    this.adding.set(true);
    this.addError.set(null);

    const code = this.addForm.value.code.toUpperCase();

    this.boss.addLivreur(code).subscribe({
      next: (livreur) => {
        this.adding.set(false);
        this.addSuccess.set(`${livreur.firstName} ${livreur.lastName} ajouté à votre équipe !`);
        this.load();
        setTimeout(() => this.closeAddModal(), 1500);
      },
      error: (err) => {
        this.adding.set(false);
        if (err.status === 404) this.addError.set('Aucun livreur trouvé avec ce code');
        else if (err.status === 409) this.addError.set(err.error?.message || 'Ce livreur est déjà dans une équipe');
        else if (err.status === 400) this.addError.set(err.error?.message || 'Code invalide');
        else this.addError.set('Erreur lors de l\'ajout');
      }
    });
  }

  confirmRemove(livreur: User): void { this.livreurToRemove.set(livreur); }
  cancelRemove(): void { this.livreurToRemove.set(null); }

  doRemove(): void {
    const livreur = this.livreurToRemove();
    if (!livreur) return;
    this.removing.set(true);
    this.boss.removeLivreur(livreur.id).subscribe({
      next: () => {
        this.removing.set(false);
        this.livreurToRemove.set(null);
        this.load();
      },
      error: () => this.removing.set(false)
    });
  }

  goToMap(livreur: User): void {
    this.router.navigate(['/boss/map'], { queryParams: { focus: livreur.livreurId } });
  }

  getPosition(livreurId?: string): LivreurPosition | null {
    if (!livreurId) return null;
    return this.positions().get(livreurId) ?? null;
  }

  getStatusColor(livreur: User): string {
    if (!livreur.active) return '#9CA3AF';
    const pos = this.getPosition(livreur.livreurId);
    if (!pos) return '#9CA3AF';
    if (pos.status === 'ALERT' || pos.drivingState === 'AGGRESSIVE') return '#EF4444';
    if (pos.drivingState === 'RISKY') return '#F59E0B';
    if (pos.status === 'OFFLINE') return '#9CA3AF';
    return '#10B981';
  }

  getStatusLabel(livreur: User): string {
    if (!livreur.active) return 'Désactivé';
    const pos = this.getPosition(livreur.livreurId);
    if (!pos) return 'Non connecté';
    const m: Record<string, string> = {
      'ACTIVE': 'En activité',
      'IDLE': 'Au repos',
      'OFFLINE': 'Hors ligne',
      'ALERT': 'Alerte'
    };
    return m[pos.status] || pos.status;
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.addForm.get(field);
    return !!(ctrl?.hasError(error) && ctrl.touched);
  }

  onSearch(value: string): void { this.search.set(value); }
}
