import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PushService {

  constructor(private http: HttpClient) {}

  /**
   * Demande permission + s'abonne aux notifications push backend.
   * À appeler dans ngOnInit du composant boss.
   */
  async subscribeToPush(): Promise<void> {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      console.warn('[Push] Non supporté');
      return;
    }

    // 1. Demander permission
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.warn('[Push] Permission refusée');
      return;
    }

    try {
      // 2. Récupérer la clé publique VAPID du backend
      const { publicKey } = await firstValueFrom(
        this.http.get<{ publicKey: string }>(`${environment.apiUrl}/api/push/vapid-public-key`)
      );

      // 3. Service Worker prêt
      const sw = await navigator.serviceWorker.ready;

      // 4. S'abonner
      let subscription = await sw.pushManager.getSubscription();
      if (!subscription) {
        subscription = await sw.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: this.urlBase64ToUint8Array(publicKey)
        });
      }

      // 5. Envoyer subscription au backend
      await firstValueFrom(
        this.http.post(`${environment.apiUrl}/api/push/subscribe`, subscription.toJSON())
      );

      console.log('[Push] Abonné avec succès ✅');
    } catch (e) {
      console.error('[Push] Erreur abonnement:', e);
    }
  }

  private urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - base64String.length % 4) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }
}
