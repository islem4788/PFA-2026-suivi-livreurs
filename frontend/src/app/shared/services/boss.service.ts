import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class BossService {
  constructor(private http: HttpClient) {}

  /** Tous les livreurs de l'équipe du boss connecté */
  getMyLivreurs(): Observable<User[]> {
    return this.http.get<User[]>(`${environment.apiUrl}/boss/livreurs`);
  }

  /** Ajoute un livreur à l'équipe via son code LIV-XXX */
  addLivreur(code: string): Observable<User> {
    return this.http.post<User>(`${environment.apiUrl}/boss/livreurs/add`, { code });
  }

  /** Retire un livreur de l'équipe (désactive son compte) */
  removeLivreur(livreurId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(
      `${environment.apiUrl}/boss/livreurs/${livreurId}`
    );
  }
}
