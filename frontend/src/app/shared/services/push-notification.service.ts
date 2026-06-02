import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {

  async subscribeToPush(): Promise<void> {
    if (!('Notification' in window) || !('serviceWorker' in navigator)) {
      console.warn('[Push] Non supporté par ce navigateur');
      return;
    }

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.warn('[Push] Permission refusée');
      return;
    }

    console.log('[Push] Permission accordée ✅');
  }

  async showLocalNotification(title: string, body: string, tag: string): Promise<void> {
    if (!('serviceWorker' in navigator)) return;
    if (Notification.permission !== 'granted') return;

    const options: NotificationOptions = {
      body,
      icon: '/icons/icon-192x192.png',
      badge: '/icons/icon-72x72.png',
      tag,
      requireInteraction: true
    };

    try {
      const sw = await navigator.serviceWorker.ready;
      await sw.showNotification(title, options);
      console.log('[Push] Notification envoyée ✅', title);
    } catch (e) {
      console.error('[Push] Erreur showNotification:', e);
      new Notification(title, options);
    }
  }
}
