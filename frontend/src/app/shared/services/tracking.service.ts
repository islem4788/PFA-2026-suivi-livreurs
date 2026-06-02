import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LivreurPosition, Alert, TrafficReport, ClientFeedback, ClientFeedbackResponse } from '../models/tracking.model';
import { ClientFeedbackEntry } from '../models/feedback.model';

@Injectable({ providedIn: 'root' })
export class TrackingService {
  constructor(private http: HttpClient) {}

  getAllLivreurs(): Observable<LivreurPosition[]> {
    return this.http.get<LivreurPosition[]>(`${environment.apiUrl}/livreurs/positions`);
  }

  getLivreurDetails(id: string): Observable<LivreurPosition> {
    return this.http.get<LivreurPosition>(`${environment.apiUrl}/livreurs/${id}`);
  }

  getAlerts(unresolved = true): Observable<Alert[]> {
    return this.http.get<Alert[]>(
      `${environment.apiUrl}/alerts?unresolved=${unresolved}`
    );
  }

  resolveAlert(id: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/alerts/${id}/resolve`, {});
  }

  postPosition(position: Partial<LivreurPosition>): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/positions`, position);
  }

  reportTraffic(report: TrafficReport): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/traffic`, report);
  }

  submitFeedback(feedback: ClientFeedback): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${environment.apiUrl}/feedback`,
      feedback
    );
  }

  getAllFeedbacks(): Observable<ClientFeedbackResponse[]> {
    return this.http.get<ClientFeedbackResponse[]>(`${environment.apiUrl}/feedback`);
  }

  getFeedbacksByPackage(packageId: string): Observable<ClientFeedbackResponse[]> {
    return this.http.get<ClientFeedbackResponse[]>(
      `${environment.apiUrl}/feedback/package/${packageId}`
    );
  }

  getAllFeedback(): Observable<ClientFeedbackEntry[]> {
    return this.http.get<ClientFeedbackEntry[]>(`${environment.apiUrl}/feedback`);
  }

  getFeedbackByPackage(packageId: string): Observable<ClientFeedbackEntry[]> {
    return this.http.get<ClientFeedbackEntry[]>(
      `${environment.apiUrl}/feedback/package/${packageId}`
    );
  }

  getEta(destLat: number, destLon: number): Observable<{ eta: number; distance: number }> {
    return this.http.get<{ eta: number; distance: number }>(
      `${environment.apiUrl}/eta?lat=${destLat}&lon=${destLon}`
    );
  }
}
