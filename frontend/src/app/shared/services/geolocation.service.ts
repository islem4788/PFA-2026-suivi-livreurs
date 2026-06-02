import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export interface GpsPosition {
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number | null;
  heading: number | null;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class GeolocationService {
  private watchId: number | null = null;
  private positionSubject = new Subject<GpsPosition>();
  public position$ = this.positionSubject.asObservable();

  public currentPosition = signal<GpsPosition | null>(null);
  public isTracking = signal<boolean>(false);
  public error = signal<string | null>(null);

  startTracking(): Observable<GpsPosition> {
    if (!('geolocation' in navigator)) {
      this.error.set('Géolocalisation non supportée par le navigateur');
      return this.position$;
    }

    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
    }

    this.watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const gps: GpsPosition = {
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracy: pos.coords.accuracy,
          speed: pos.coords.speed,
          heading: pos.coords.heading,
          timestamp: pos.timestamp
        };
        this.currentPosition.set(gps);
        this.positionSubject.next(gps);
        this.error.set(null);
      },
      (err) => {
        this.error.set(this.getErrorMessage(err));
      },
      {
        enableHighAccuracy: true,
        maximumAge: 0,
        timeout: 10000
      }
    );

    this.isTracking.set(true);
    return this.position$;
  }

  stopTracking(): void {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    this.isTracking.set(false);
  }

  private getErrorMessage(err: GeolocationPositionError): string {
    switch (err.code) {
      case err.PERMISSION_DENIED:
        return 'Permission GPS refusée';
      case err.POSITION_UNAVAILABLE:
        return 'Position indisponible';
      case err.TIMEOUT:
        return 'Délai dépassé';
      default:
        return 'Erreur inconnue';
    }
  }
}
