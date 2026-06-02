import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TrackingService } from '../../shared/services/tracking.service';
import { ClientFeedbackResponse } from '../../shared/models/tracking.model';

@Component({
  selector: 'app-avis-clients',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  templateUrl: './avis-clients.component.html',
  styleUrls: ['./avis-clients.component.scss']
})
export class AvisClientsComponent implements OnInit {
  feedbacks = signal<ClientFeedbackResponse[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  search = signal<string>('');
  filterRating = signal<number | null>(null);
  selectedFeedback = signal<ClientFeedbackResponse | null>(null);

  filtered = computed(() => {
    let list = this.feedbacks();
    const q = this.search().toLowerCase().trim();
    if (q) {
      list = list.filter(f =>
        f.clientName.toLowerCase().includes(q) ||
        f.packageId.toLowerCase().includes(q) ||
        f.comment.toLowerCase().includes(q)
      );
    }
    const r = this.filterRating();
    if (r !== null) {
      list = list.filter(f => f.rating === r);
    }
    return list;
  });

  total = computed(() => this.feedbacks().length);

  averageRating = computed(() => {
    const rated = this.feedbacks().filter(f => f.rating !== undefined && f.rating !== null);
    if (rated.length === 0) return 0;
    const sum = rated.reduce((acc, f) => acc + (f.rating || 0), 0);
    return sum / rated.length;
  });

  positiveCount = computed(() =>
    this.feedbacks().filter(f => (f.rating || 0) >= 4).length
  );

  negativeCount = computed(() =>
    this.feedbacks().filter(f => (f.rating || 0) > 0 && (f.rating || 0) <= 2).length
  );

  noRatingCount = computed(() =>
    this.feedbacks().filter(f => !f.rating).length
  );

  constructor(private tracking: TrackingService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.tracking.getAllFeedbacks().subscribe({
      next: (data) => {
        this.feedbacks.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les avis');
        this.loading.set(false);
      }
    });
  }

  setFilterRating(r: number | null): void {
    this.filterRating.set(r);
  }

  isFilterActive(r: number | null): boolean {
    return this.filterRating() === r;
  }

  openDetails(f: ClientFeedbackResponse): void {
    this.selectedFeedback.set(f);
  }

  closeDetails(): void {
    this.selectedFeedback.set(null);
  }

  getRatingColor(rating?: number): string {
    if (!rating) return '#9CA3AF';
    if (rating >= 4) return '#10B981';
    if (rating >= 3) return '#F59E0B';
    return '#EF4444';
  }

  getRatingLabel(rating?: number): string {
    if (!rating) return 'Non noté';
    if (rating === 5) return 'Excellent';
    if (rating === 4) return 'Bon';
    if (rating === 3) return 'Correct';
    if (rating === 2) return 'Insatisfaisant';
    return 'Très insatisfaisant';
  }

  isPositive(f: ClientFeedbackResponse): boolean {
    return (f.rating || 0) >= 4;
  }

  isNegative(f: ClientFeedbackResponse): boolean {
    return (f.rating || 0) > 0 && (f.rating || 0) <= 2;
  }

  truncate(text: string, n: number = 120): string {
    return text.length <= n ? text : text.substring(0, n) + '...';
  }

  onSearchChange(value: string): void {
    this.search.set(value);
  }
}
