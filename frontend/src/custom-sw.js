// custom-sw.js — Service Worker OINSExpress
// Reçoit les push notifications du backend même app fermée

importScripts('ngsw-worker.js');

self.addEventListener('push', function(event) {
  console.log('[SW] Push reçu:', event);

  let data = {
    title: '🔔 OINSExpress',
    body: 'Nouvelle alerte',
    url: '/boss/map'
  };

  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data.body = event.data.text();
    }
  }

  const options = {
    body: data.body,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-72x72.png',
    tag: 'oinsexpress-alert',
    requireInteraction: true,
    data: { url: data.url || '/boss/map' }
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

self.addEventListener('notificationclick', function(event) {
  event.notification.close();
  const url = event.notification.data?.url || '/boss/map';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then(function(clientList) {
        for (const client of clientList) {
          if (client.url.includes('oinsexpress') && 'focus' in client) {
            client.navigate(url);
            return client.focus();
          }
        }
        if (clients.openWindow) {
          return clients.openWindow(url);
        }
      })
  );
});
